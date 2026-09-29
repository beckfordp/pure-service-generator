package codegen

/** Per-type syntax primitives and the per-anchor-tag cell renderers used by
  * [[AnchorTransformer]]. Every tag maps to a single `Field => String` cell
  * renderer - the same cell text is reused whether the anchor turns out to be
  * an inline marker or an own-line one; [[AnchorTransformer]] decides how to
  * join/place the cells based on the surrounding text, not the tag.
  *
  * [[visibilityFilters]] decides, per tag, which subset of a field-spec's
  * fields that anchor actually renders - most tags render every field (the
  * entity/response/SQL row always carries every field regardless of how it's
  * set), but create-side tags exclude `server-defaulted` fields and update-side
  * tags exclude `create-only` fields, mirroring the three visibility patterns
  * the former fixed item/quantity/status fields exhibited.
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

  /** The expression converting a raw column value (already bound to
    * `varName`via [[sqlParamScalaType]]) back to the domain field's type.
    */
  def fromSqlToDomain(f: Field, varName: String): String = f.`type` match
    case FieldType.InstantType => s"$varName.toInstant"
    case _                     => varName

  /** Renders a literal of `fieldType`'s Scala type from a raw string value (a
    * field's `example` or a `server-defaulted` field's `default`), for splicing
    * directly into generated Scala source.
    */
  def literalOf(fieldType: FieldType, value: String): String = fieldType match
    case FieldType.StringType =>
      "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    case FieldType.IntType     => value
    case FieldType.BooleanType => value
    case FieldType.InstantType => s"""java.time.Instant.parse("$value")"""

  def literalExample(f: Field): String = literalOf(f.`type`, f.example)

  def literalDefault(f: Field): String =
    literalOf(f.`type`, f.default.getOrElse(""))

  private val camelBoundary = "([a-z0-9])([A-Z])".r

  def snakeCase(name: String): String =
    camelBoundary
      .replaceAllIn(name, m => s"${m.group(1)}_${m.group(2)}")
      .toLowerCase

  def capitalize(name: String): String =
    if name.isEmpty then name else name.head.toUpper +: name.tail

  /** The `private val default<Field>` constant name for a server-defaulted
    * field (matching the pre-generalization `defaultStatus` pattern).
    */
  def defaultConstantName(f: Field): String = s"default${capitalize(f.name)}"

  /** [[CONSTRUCT_ARGS]]'s cell: the create-time value used to build the domain
    * entity - the create param itself for create-and-update/ create-only
    * fields, or the `default<Field>` constant for server-defaulted fields
    * (which have no create param at all).
    */
  private def constructArg(f: Field): String =
    if f.visibility == Visibility.ServerDefaulted then defaultConstantName(f)
    else f.name

  /** [[SQL_INSERT_TUPLE_ARGS]]'s cell: like [[constructArg]], but through
    * [[sqlWriteExpr]] for the SQL-write conversion (e.g. Instant ->
    * OffsetDateTime).
    */
  private def sqlInsertTupleArg(f: Field): String =
    if f.visibility == Visibility.ServerDefaulted then
      sqlWriteExpr(f, defaultConstantName(f))
    else sqlWriteExpr(f, f.name)

  /** Anchor tags whose own-line form is a sequence of independent statements
    * (no trailing commas, no fixup of the preceding line) rather than a
    * comma-separated list. Every other tag defaults to comma-list mode.
    */
  val statementModeTags: Set[String] =
    Set("TEST_LIFECYCLE_ASSERT", "DEFAULT_VALUE_DECLS")

  private val allFields: Field => Boolean = _ => true
  private val excludeServerDefaulted: Field => Boolean =
    _.visibility != Visibility.ServerDefaulted
  private val excludeCreateOnly: Field => Boolean =
    _.visibility != Visibility.CreateOnly
  private val onlyServerDefaulted: Field => Boolean =
    _.visibility == Visibility.ServerDefaulted

  /** Which fields a given anchor tag renders, out of the full field-spec. */
  val visibilityFilters: Map[String, Field => Boolean] = Map(
    "CASE_CLASS_FIELD" -> allFields,
    "CREATE_PARAMS" -> excludeServerDefaulted,
    "UPDATE_PARAMS" -> excludeCreateOnly,
    "CONSTRUCT_ARGS" -> allFields,
    "COPY_ARGS" -> excludeCreateOnly,
    "CREATE_CALL_ARGS" -> excludeServerDefaulted,
    "UPDATE_CALL_ARGS" -> excludeCreateOnly,
    "RESPONSE_APPLY_ARGS" -> allFields,
    "SQL_CREATE_COLUMN" -> allFields,
    "SQL_INSERT_COLUMNS" -> allFields,
    "SQL_INSERT_PARAMS" -> allFields,
    "SQL_INSERT_TUPLE_TYPE" -> allFields,
    "SQL_INSERT_TUPLE_ARGS" -> allFields,
    "SQL_SELECT_TUPLE_TYPE" -> allFields,
    "SQL_SELECT_COLUMNS" -> allFields,
    "SQL_SELECT_CODEC" -> allFields,
    "SQL_SELECT_CONSTRUCT_ARGS" -> allFields,
    "SQL_SELECT_PATTERN_VARS" -> allFields,
    "SQL_UPDATE_TUPLE_TYPE" -> excludeCreateOnly,
    "SQL_UPDATE_SET" -> excludeCreateOnly,
    "SQL_UPDATE_TUPLE_ARGS" -> excludeCreateOnly,
    "TEST_CREATE_ARGS" -> excludeServerDefaulted,
    "TEST_UPDATE_ARGS" -> excludeCreateOnly,
    "TEST_CREATE_REQUEST_ARGS" -> excludeServerDefaulted,
    "TEST_UPDATE_REQUEST_ARGS" -> excludeCreateOnly,
    "TEST_MIGRATION_COLUMN" -> allFields,
    "TEST_LIFECYCLE_ASSERT" -> allFields,
    "DEFAULT_VALUE_DECLS" -> onlyServerDefaulted
  )

  val cellRenderers: Map[String, Field => String] = Map(
    "CASE_CLASS_FIELD" -> (f => s"${f.name}: ${scalaType(f)}"),
    "CREATE_PARAMS" -> (f => s"${f.name}: ${scalaType(f)}"),
    "UPDATE_PARAMS" -> (f => s"${f.name}: ${scalaType(f)}"),
    "CONSTRUCT_ARGS" -> constructArg,
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
    "SQL_INSERT_TUPLE_ARGS" -> sqlInsertTupleArg,
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
    ),
    "DEFAULT_VALUE_DECLS" -> (f =>
      s"private val ${defaultConstantName(f)} = ${literalDefault(f)}"
    )
  )
