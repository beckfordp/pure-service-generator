package codegen

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

/** Rewrites a single generated source/SQL/test file by expanding every
  * `codegen:fields:<TAG>` anchor left in it (see the g8 template's anchor
  * comments) into one rendered fragment per field in the spec.
  *
  * Two anchor forms are recognized, distinguished purely by what remains on the
  * line once the marker text itself is removed - not by which comment syntax it
  * uses:
  *   - own-line: the marker is the only non-whitespace content on its line (`//
  *     codegen:fields:TAG`, `-- codegen:fields:TAG`, or an isolated
  *     `/* codegen:fields:TAG */`) - replaced by one full line per field.
  *   - inline: the marker sits alongside other code on the line (`/*
  *     codegen:fields:TAG */`) - replaced in place by fragments joined to match
  *     whatever precedes it (a bare item, a trailing ", ", or a Skunk `*: `
  *     combinator chain).
  */
object AnchorTransformer:

  private val lineCommentAnchor: Regex =
    """^(\s*)(?://|--)\s*codegen:fields:(\w+)\s*$""".r
  private val blockCommentAnchor: Regex =
    """/\*\s*codegen:fields:(\w+)\s*\*/""".r

  def transform(content: String, fields: List[Field]): String =
    val lines = content.split("\n", -1)
    val output = ArrayBuffer.empty[String]
    var i = 0
    while i < lines.length do
      val line = lines(i)
      def nextLine: Option[String] =
        Option.when(i + 1 < lines.length)(lines(i + 1))

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
                output += prefix + inlineFragments(tag, fields, prefix) + suffix
            case None =>
              output += line
      i += 1

    output.mkString("\n")

  private def renderer(tag: String): Field => String =
    FieldRenderers.cellRenderers.getOrElse(
      tag,
      throw new IllegalArgumentException(
        s"Unknown codegen:fields anchor tag '$tag'"
      )
    )

  private def inlineFragments(
      tag: String,
      fields: List[Field],
      prefix: String
  ): String =
    val cell = renderer(tag)
    val trimmedPrefix = prefix.replaceAll("\\s+$", "")
    if trimmedPrefix.endsWith("*:") then
      fields.map(f => cell(f) + " *: ").mkString("")
    else if trimmedPrefix.endsWith(",") then
      fields.map(f => cell(f) + ", ").mkString("")
    else fields.map(f => ", " + cell(f)).mkString("")

  private def isClosingDelimiter(line: String): Boolean =
    val t = line.trim
    t.startsWith(")") || t.startsWith("}") || t.startsWith("]")

  private def appendOwnLine(
      output: ArrayBuffer[String],
      indent: String,
      tag: String,
      fields: List[Field],
      nextLine: Option[String]
  ): Unit =
    val cell = renderer(tag)
    if FieldRenderers.statementModeTags.contains(tag) then
      fields.foreach(f => output += indent + cell(f))
    else
      ensureTrailingComma(output)
      val isLastItemInList = nextLine.exists(isClosingDelimiter)
      fields.zipWithIndex.foreach { case (f, idx) =>
        val isLastField = idx == fields.length - 1
        val comma = if isLastItemInList && isLastField then "" else ","
        output += indent + cell(f) + comma
      }

  private def ensureTrailingComma(output: ArrayBuffer[String]): Unit =
    val idx = output.lastIndexWhere(_.trim.nonEmpty)
    if idx >= 0 then
      val line = output(idx)
      if !line.replaceAll("\\s+$", "").endsWith(",") then
        output(idx) = line.replaceAll("\\s+$", "") + ","
