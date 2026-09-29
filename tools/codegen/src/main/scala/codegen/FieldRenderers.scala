package codegen

/** Per-type syntax primitives and the per-anchor-tag cell renderers used by
  * [[AnchorTransformer]]. Every tag maps to a single `Field => String` cell
  * renderer - the same cell text is reused whether the anchor turns out to be
  * an inline marker or an own-line one; [[AnchorTransformer]] decides how to
  * join/place the cells based on the surrounding text, not the tag.
  */
object FieldRenderers:

  def scalaType(f: Field): String = f.`type` match
    case FieldType.StringType  => "String"
    case FieldType.IntType     => "Int"
    case FieldType.BooleanType => "Boolean"
    case FieldType.InstantType => "java.time.Instant"

  /** The Scala type Skunk expects at the codec boundary - identical to
    * [[scalaType]] except for `Instant`, which round-trips through the database
    * as `OffsetDateTime` (matching the existing createdAt/updatedAt pattern)
    * and is converted at the domain boundary instead.
    */
  def sqlParamScalaType(f: Field): String = f.`type` match
    case FieldType.InstantType => "OffsetDateTime"
    case _                     => scalaType(f)

  def codecName(f: Field): String = f.`type` match
    case FieldType.StringType  => "text"
    case FieldType.IntType     => "int4"
    case FieldType.BooleanType => "bool"
    case FieldType.InstantType => "timestamptz"

  def ddlType(f: Field): String = f.`type` match
    case FieldType.StringType  => "TEXT"
    case FieldType.IntType     => "INT"
    case FieldType.BooleanType => "BOOLEAN"
    case FieldType.InstantType => "TIMESTAMPTZ"

  /** The expression to pass to Skunk when writing `paramName` (a domain-typed
    * value) into an insert/update query.
    */
  def sqlWriteExpr(f: Field, paramName: String): String = f.`type` match
    case FieldType.InstantType =>
      s"$paramName.atOffset(java.time.ZoneOffset.UTC)"
    case _ => paramName

  /** The expression converting a raw column value (already bound to `varName`
    * via [[sqlParamScalaType]]) back to the domain field's type.
    */
  def fromSqlToDomain(f: Field, varName: String): String = f.`type` match
    case FieldType.InstantType => s"$varName.toInstant"
    case _                     => varName

  /** Renders a field's YAML `example` as a Scala literal of the right type, for
    * splicing into rewritten test call sites.
    */
  def literalExample(f: Field): String = f.`type` match
    case FieldType.StringType =>
      "\"" + f.example.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    case FieldType.IntType     => f.example
    case FieldType.BooleanType => f.example
    case FieldType.InstantType => s"""java.time.Instant.parse("${f.example}")"""

  private val camelBoundary = "([a-z0-9])([A-Z])".r

  def snakeCase(name: String): String =
    camelBoundary
      .replaceAllIn(name, m => s"${m.group(1)}_${m.group(2)}")
      .toLowerCase

  /** Anchor tags whose own-line form is a sequence of independent statements
    * (no trailing commas, no fixup of the preceding line) rather than a
    * comma-separated list. Every other tag defaults to comma-list mode.
    */
  val statementModeTags: Set[String] = Set("TEST_LIFECYCLE_ASSERT")

  val cellRenderers: Map[String, Field => String] = Map(
    "CASE_CLASS_FIELD" -> (f => s"${f.name}: ${scalaType(f)}"),
    "CREATE_PARAMS" -> (f => s"${f.name}: ${scalaType(f)}"),
    "UPDATE_PARAMS" -> (f => s"${f.name}: ${scalaType(f)}"),
    "CONSTRUCT_ARGS" -> (f => f.name),
    "COPY_ARGS" -> (f => s"${f.name} = ${f.name}"),
    "CREATE_CALL_ARGS" -> (f => s"req.${f.name}"),
    "UPDATE_CALL_ARGS" -> (f => s"req.${f.name}"),
    "RESPONSE_APPLY_ARGS" -> (f => s"entity.${f.name}"),
    "SQL_CREATE_COLUMN" -> (f =>
      s"${snakeCase(f.name)} ${ddlType(f)} NOT NULL"
    ),
    "SQL_INSERT_COLUMNS" -> (f => snakeCase(f.name)),
    "SQL_INSERT_PARAMS" -> (f => s"$$${codecName(f)}"),
    "SQL_INSERT_TUPLE_TYPE" -> (f => sqlParamScalaType(f)),
    "SQL_INSERT_TUPLE_ARGS" -> (f => sqlWriteExpr(f, f.name)),
    "SQL_SELECT_TUPLE_TYPE" -> (f => sqlParamScalaType(f)),
    "SQL_SELECT_COLUMNS" -> (f => snakeCase(f.name)),
    "SQL_SELECT_CODEC" -> (f => codecName(f)),
    "SQL_SELECT_CONSTRUCT_ARGS" -> (f => fromSqlToDomain(f, f.name)),
    "SQL_SELECT_PATTERN_VARS" -> (f => f.name),
    "SQL_UPDATE_TUPLE_TYPE" -> (f => sqlParamScalaType(f)),
    "SQL_UPDATE_SET" -> (f => s"${snakeCase(f.name)} = $$${codecName(f)}"),
    "SQL_UPDATE_TUPLE_ARGS" -> (f => sqlWriteExpr(f, f.name)),
    "TEST_CREATE_ARGS" -> (f => literalExample(f)),
    "TEST_UPDATE_ARGS" -> (f => literalExample(f)),
    "TEST_CREATE_REQUEST_ARGS" -> (f => literalExample(f)),
    "TEST_UPDATE_REQUEST_ARGS" -> (f => literalExample(f)),
    "TEST_MIGRATION_COLUMN" -> (f => "\"" + snakeCase(f.name) + "\""),
    "TEST_LIFECYCLE_ASSERT" -> (f =>
      s"assertEquals(updated.map(_.${f.name}), Some(${literalExample(f)}))"
    )
  )
