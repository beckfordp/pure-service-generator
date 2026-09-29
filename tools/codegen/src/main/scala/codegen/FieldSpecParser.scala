package codegen

import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.yaml.{parser as yamlParser}

import scala.util.Try

object FieldSpecParser:

  given Decoder[FieldType] = Decoder.decodeString.emap(FieldType.fromString)
  given Decoder[Visibility] = Decoder.decodeString.emap(Visibility.fromString)

  given Decoder[Field] = (c: HCursor) =>
    for
      name <- c.downField("name").as[String]
      fieldType <- c.downField("type").as[FieldType]
      example <- c.downField("example").as[String]
      _ <- validateLiteral("example", fieldType, example, c)
      visibility <- c
        .downField("visibility")
        .as[Option[Visibility]]
        .map(_.getOrElse(Visibility.CreateAndUpdate))
      defaultOpt <- c.downField("default").as[Option[String]]
      _ <- validateDefault(fieldType, visibility, defaultOpt, c)
    yield Field(name, fieldType, example, visibility, defaultOpt)

  /** Fails fast, at field-spec parse time, when a literal (`example`, or a
    * `server-defaulted` field's `default`) doesn't parse as its declared `type` -
    * otherwise the mismatch only surfaces as a confusing Scala compile error in
    * the *generated* project, once [[FieldRenderers.literalExample]] has
    * already spliced it in verbatim.
    */
  private def validateLiteral(
      keyName: String,
      fieldType: FieldType,
      value: String,
      c: HCursor
  ): Decoder.Result[Unit] =
    val isValid = fieldType match
      case FieldType.StringType  => true
      case FieldType.IntType     => value.toIntOption.isDefined
      case FieldType.BooleanType => value.toBooleanOption.isDefined
      case FieldType.InstantType =>
        Try(java.time.Instant.parse(value)).isSuccess
    if isValid then Right(())
    else
      Left(
        DecodingFailure(
          s"$keyName '$value' is not a valid $fieldType literal",
          c.history
        )
      )

  /** `default` is required for `server-defaulted` fields (the value used for
    * the `private val default<Field>` constant) and rejected for every other
    * visibility, since it would otherwise be silently ignored.
    */
  private def validateDefault(
      fieldType: FieldType,
      visibility: Visibility,
      defaultOpt: Option[String],
      c: HCursor
  ): Decoder.Result[Unit] =
    (visibility, defaultOpt) match
      case (Visibility.ServerDefaulted, None) =>
        Left(
          DecodingFailure(
            "server-defaulted fields require a 'default' value",
            c.history
          )
        )
      case (Visibility.ServerDefaulted, Some(default)) =>
        validateLiteral("default", fieldType, default, c)
      case (_, Some(_)) =>
        Left(
          DecodingFailure(
            "'default' is only valid for server-defaulted fields",
            c.history
          )
        )
      case (_, None) => Right(())

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
