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

/** How a field participates in create/update, mirroring the three patterns the
  * former fixed item/quantity/status fields exhibited.
  */
enum Visibility:
  /** Client sets it at create, can change it via update (the default). */
  case CreateAndUpdate

  /** Client sets it at create, immutable after - excluded from
    * `UpdateRequest`/update SQL/update test call sites.
    */
  case CreateOnly

  /** Server assigns a default value at create (never client-settable there);
    * settable via update like [[CreateAndUpdate]].
    */
  case ServerDefaulted

object Visibility:
  def fromString(raw: String): Either[String, Visibility] = raw match
    case "create-and-update" => Right(CreateAndUpdate)
    case "create-only"       => Right(CreateOnly)
    case "server-defaulted"  => Right(ServerDefaulted)
    case other               =>
      Left(
        s"Unknown visibility '$other' - supported: create-and-update, create-only, server-defaulted"
      )

final case class Field(
    name: String,
    `type`: FieldType,
    example: String,
    visibility: Visibility = Visibility.CreateAndUpdate,
    default: Option[String] = None
)

final case class FieldSpec(fields: List[Field])
