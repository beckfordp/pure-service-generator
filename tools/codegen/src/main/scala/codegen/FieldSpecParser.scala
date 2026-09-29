package codegen

import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.yaml.{parser as yamlParser}

object FieldSpecParser:

  given Decoder[FieldType] = Decoder.decodeString.emap(FieldType.fromString)

  given Decoder[Field] = (c: HCursor) =>
    for
      name <- c.downField("name").as[String]
      fieldType <- c.downField("type").as[FieldType]
      example <- c.downField("example").as[String]
    yield Field(name, fieldType, example)

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
