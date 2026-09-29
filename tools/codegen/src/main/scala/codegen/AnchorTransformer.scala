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
  *
  * An unrecognized tag (a version mismatch between this tool and the template's
  * anchor catalog) is a nameable, expected failure - reported as `Left`, never
  * thrown.
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
                  inlineFragments(tag, fields, prefix).map(frag =>
                    output += prefix + frag + suffix
                  )
              case None =>
                output += line
                Right(())

      outcome.left.foreach(e => error = Some(e))
      i += 1

    error.toLeft(output.mkString("\n"))

  private def renderer(tag: String): Either[String, Field => String] =
    FieldRenderers.cellRenderers
      .get(tag)
      .toRight(s"Unknown codegen:fields anchor tag '$tag'")

  private def inlineFragments(
      tag: String,
      fields: List[Field],
      prefix: String
  ): Either[String, String] =
    renderer(tag).map { cell =>
      val trimmedPrefix = prefix.replaceAll("\\s+$", "")
      if trimmedPrefix.endsWith("*:") then
        fields.map(f => cell(f) + " *: ").mkString("")
      else if trimmedPrefix.endsWith(",") then
        fields.map(f => cell(f) + ", ").mkString("")
      else fields.map(f => ", " + cell(f)).mkString("")
    }

  private def isClosingDelimiter(line: String): Boolean =
    val t = line.trim
    t.startsWith(")") || t.startsWith("}") || t.startsWith("]")

  private def appendOwnLine(
      output: ArrayBuffer[String],
      indent: String,
      tag: String,
      fields: List[Field],
      nextLine: Option[String]
  ): Either[String, Unit] =
    renderer(tag).map { cell =>
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
    }

  private def ensureTrailingComma(output: ArrayBuffer[String]): Unit =
    val idx = output.lastIndexWhere(_.trim.nonEmpty)
    if idx >= 0 then
      val line = output(idx)
      if !line.replaceAll("\\s+$", "").endsWith(",") then
        output(idx) = line.replaceAll("\\s+$", "") + ","
