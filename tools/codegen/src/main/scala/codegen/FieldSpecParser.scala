package codegen

import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.yaml.{parser as yamlParser}

import scala.util.Try

object FieldSpecParser:

  given Decoder[FieldType] = Decoder.decodeString.emap(FieldType.fromString)

  given Decoder[Field] = (c: HCursor) =>
    for
      name <- c.downField("name").as[String]
      fieldType <- c.downField("type").as[FieldType]
      example <- c.downField("example").as[String]
      _ <- validateExample(fieldType, example, c)
    yield Field(name, fieldType, example)

  /** Fails fast, at field-spec parse time, when `example` doesn't parse as a
    * literal of its declared `type` - otherwise the mismatch only surfaces as a
    * confusing Scala compile error in the *generated* project, once
    * [[FieldRenderers.literalExample]] has already spliced it in verbatim.
    */
  private def validateExample(
      fieldType: FieldType,
      example: String,
      c: HCursor
  ): Decoder.Result[Unit] =
    val isValid = fieldType match
      case FieldType.StringType  => true
      case FieldType.IntType     => example.toIntOption.isDefined
      case FieldType.BooleanType => example.toBooleanOption.isDefined
      case FieldType.InstantType =>
        Try(java.time.Instant.parse(example)).isSuccess
    if isValid then Right(())
    else
      Left(
        DecodingFailure(
          s"example '$example' is not a valid $fieldType literal",
          c.history
        )
      )

  given Decoder[FieldSpec] = (c: HCursor) =>
    c.downField("fields").as[List[Field]].flatMap { fields =>
      if fields.isEmpty then
        Left(DecodingFailure("fields must not be empty", c.history))
      else Right(FieldSpec(fields))
    }

  def parse(yaml: String): Either[String, FieldSpec] =
    yamlParser
      .parse(yaml)
      .left
      .map(_.getMessage)
      .flatMap(_.as[FieldSpec].left.map(_.getMessage))
