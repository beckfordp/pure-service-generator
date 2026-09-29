package codegen

enum FieldType:
  case StringType, IntType, BooleanType, InstantType

object FieldType:
  def fromString(raw: String): Either[String, FieldType] = raw match
    case "String"  => Right(StringType)
    case "Int"     => Right(IntType)
    case "Boolean" => Right(BooleanType)
    case "Instant" => Right(InstantType)
    case other     =>
      Left(
        s"Unknown field type '$other' - supported types: String, Int, Boolean, Instant"
      )

final case class Field(name: String, `type`: FieldType, example: String)

final case class FieldSpec(fields: List[Field])
