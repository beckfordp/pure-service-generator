package codegen

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

/** Rewrites a single generated source/SQL/test file by expanding every
  * `codegen:fields:<TAG>` anchor left in it (see the g8 template's anchor
  * comments) into one rendered fragment per field in the spec that tag's
  * [[FieldRenderers.visibilityFilters]] selects.
  *
  * Two anchor forms are recognized, distinguished purely by what remains on
  * the line once the marker text itself is removed - not by which comment
  * syntax it uses:
  *   - own-line: the marker is the only non-whitespace content on its line
  *     (`// codegen:fields:TAG`, `-- codegen:fields:TAG`, or an isolated
  *     `/* codegen:fields:TAG */`) - replaced by one full line per field.
  *   - inline: the marker sits alongside other code on the line
  *     (`/* codegen:fields:TAG */`) - replaced in place by fragments joined
  *     to match whatever precedes/follows it.
  *
  * The join style is auto-detected from the immediate neighbors on both
  * sides, not hardcoded per tag - since the base entity's fixed fields were
  * removed (this project's `base-fields-spec` track), a tag's anchor may sit
  * with nothing at all preceding it (`create(/* MARKER */)`), between a
  * SQL keyword and a fixed non-empty remainder (`SET /* MARKER */updated_at
  * = now()`), or render zero fields entirely when every field in the spec is
  * filtered out for that tag (e.g. every field is `create-only`, so
  * `UPDATE_PARAMS` has nothing to add) - in which case the marker vanishes
  * and whatever surrounds it is left exactly as it was.
  *
  * An unrecognized tag (a version mismatch between this tool and the
  * template's anchor catalog) is a nameable, expected failure - reported as
  * `Left`, never thrown.
  */
object AnchorTransformer:

  private val lineCommentAnchor: Regex =
    """^(\s*)(?://|--)\s*codegen:fields:(\w+)\s*$""".r
  private val blockCommentAnchor: Regex =
    """/\*\s*codegen:fields:(\w+)\s*\*/""".r

  def transform(content: String, fields: List[Field]): Either[String, String] =
    val lines = content.split("\n", -1)
    val output = ArrayBuffer.empty[String]
    var i = 0
    var error: Option[String] = None

    while i < lines.length && error.isEmpty do
      val line = lines(i)
      def nextLine: Option[String] =
        Option.when(i + 1 < lines.length)(lines(i + 1))

      val outcome: Either[String, Unit] =
        line match
          case lineCommentAnchor(indent, tag) =>
            appendOwnLine(output, indent, tag, fields, nextLine)
          case _ =>
            blockCommentAnchor.findFirstMatchIn(line) match
              case Some(m) =>
                val tag = m.group(1)
                val withoutMarker =
                  line.substring(0, m.start) + line.substring(m.end)
                if withoutMarker.trim.isEmpty then
                  val indent = line.takeWhile(_.isWhitespace)
                  appendOwnLine(output, indent, tag, fields, nextLine)
                else
                  val prefix = line.substring(0, m.start)
                  val suffix = line.substring(m.end)
                  inlineFragment(tag, fields, prefix, suffix).map(frag =>
                    output += prefix + frag + suffix
                  )
              case None =>
                output += line
                Right(())

      outcome.left.foreach(e => error = Some(e))
      i += 1

    error.toLeft(output.mkString("\n"))

  private def lookup(
      tag: String
  ): Either[String, (Field => String, Field => Boolean)] =
    (
      FieldRenderers.cellRenderers.get(tag),
      FieldRenderers.visibilityFilters.get(tag)
    ) match
      case (Some(cell), Some(filter)) => Right((cell, filter))
      case _ => Left(s"Unknown codegen:fields anchor tag '$tag'")

  // --- boundary classification -------------------------------------------
  //
  // Whether a leading/trailing separator is needed around the inserted
  // fields is decided purely from the literal text immediately before and
  // after the marker - not from the tag - so it works whether the marker
  // has real content before it (`Int/* MARKER */`), sits right after an
  // opening bracket or SQL keyword with nothing before it at all
  // (`create(/* MARKER */)`, `SET /* MARKER */...`), or has a fixed,
  // non-empty remainder immediately after it with no separator of its own
  // (`(/* MARKER */UUID)`).

  private def isOpenBracket(t: String): Boolean =
    t.endsWith("(") || t.endsWith("[") || t.endsWith("{")

  private def isCloseBracket(t: String): Boolean =
    t.startsWith(")") || t.startsWith("]") || t.startsWith("}")

  /** SQL keywords that introduce a list the same way an opening bracket does -
    * nothing needs to precede the first inserted field.
    */
  private val listOpeningKeywords = List("SELECT", "SET", "RETURNING", "VALUES")

  private def endsWithListKeyword(t: String): Boolean =
    listOpeningKeywords.exists(kw => t.matches(s".*\\b$kw"))

  private def leadingSeparatorNeeded(prefix: String): Boolean =
    val t = prefix.replaceAll("\\s+$", "")
    t.nonEmpty && !isOpenBracket(t) && !endsWithListKeyword(t) && !t.endsWith(
      ","
    ) && !t
      .endsWith("*:")

  /** SQL_SELECT_CODEC is inherently a Skunk codec-combinator (`*:`) join,
    * regardless of what precedes the marker - with zero base fields, nothing
    * may precede it at all (`.query(/* MARKER */timestamptz ...)`), so
    * combinator-mode can't always be inferred purely from the prefix text.
    */
  private val alwaysCombinatorTags: Set[String] = Set("SQL_SELECT_CODEC")

  private def isCombinatorContext(tag: String, prefix: String): Boolean =
    alwaysCombinatorTags
      .contains(tag) || prefix.replaceAll("\\s+$", "").endsWith("*:")

  private def trailingSeparatorNeeded(suffix: String): Boolean =
    val t = suffix.replaceAll("^\\s+", "")
    t.nonEmpty && !isCloseBracket(t)

  private def inlineFragment(
      tag: String,
      fields: List[Field],
      prefix: String,
      suffix: String
  ): Either[String, String] =
    lookup(tag).map { case (cell, filter) =>
      val filtered = fields.filter(filter)
      if filtered.isEmpty then ""
      else
        val combinator = isCombinatorContext(tag, prefix)
        val sep = if combinator then " *: " else ", "
        val lead = if leadingSeparatorNeeded(prefix) then sep else ""
        val body =
          if trailingSeparatorNeeded(suffix) then
            filtered.map(f => cell(f) + sep).mkString("")
          else filtered.map(cell).mkString(sep)
        lead + body
    }

  private def isClosingDelimiter(line: String): Boolean =
    val t = line.trim
    t.startsWith(")") || t.startsWith("}") || t.startsWith("]")

  private def precedingLineEndsOpen(output: ArrayBuffer[String]): Boolean =
    val idx = output.lastIndexWhere(_.trim.nonEmpty)
    idx >= 0 && isOpenBracket(output(idx).replaceAll("\\s+$", ""))

  private def appendOwnLine(
      output: ArrayBuffer[String],
      indent: String,
      tag: String,
      fields: List[Field],
      nextLine: Option[String]
  ): Either[String, Unit] =
    lookup(tag).map { case (cell, filter) =>
      val filtered = fields.filter(filter)
      if FieldRenderers.statementModeTags.contains(tag) then
        filtered.foreach(f => output += indent + cell(f))
      else if filtered.nonEmpty then
        if !precedingLineEndsOpen(output) then ensureTrailingComma(output)
        val isLastItemInList = nextLine.exists(isClosingDelimiter)
        filtered.zipWithIndex.foreach { case (f, idx) =>
          val isLastField = idx == filtered.length - 1
          val comma = if isLastItemInList && isLastField then "" else ","
          output += indent + cell(f) + comma
        }
    // else: filtered.isEmpty && not statement mode -> nothing to render;
    // the marker line vanishes and the preceding line is left untouched.
    }

  private def ensureTrailingComma(output: ArrayBuffer[String]): Unit =
    val idx = output.lastIndexWhere(_.trim.nonEmpty)
    if idx >= 0 then
      val line = output(idx)
      if !line.replaceAll("\\s+$", "").endsWith(",") then
        output(idx) = line.replaceAll("\\s+$", "") + ","
