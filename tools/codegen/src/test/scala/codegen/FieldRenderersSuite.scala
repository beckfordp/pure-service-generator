package codegen

import munit.FunSuite

class FieldRenderersSuite extends FunSuite {

  private val str = Field("color", FieldType.StringType, "red")
  private val int = Field("weight", FieldType.IntType, "42")
  private val bool = Field("fragile", FieldType.BooleanType, "true")
  private val instant =
    Field("expiresAt", FieldType.InstantType, "2026-01-01T00:00:00Z")

  test("scalaType maps each FieldType to its Scala type name") {
    assertEquals(FieldRenderers.scalaType(str), "String")
    assertEquals(FieldRenderers.scalaType(int), "Int")
    assertEquals(FieldRenderers.scalaType(bool), "Boolean")
    assertEquals(FieldRenderers.scalaType(instant), "java.time.Instant")
  }

  test(
    "sqlParamScalaType matches scalaType except Instant, which uses OffsetDateTime"
  ) {
    assertEquals(FieldRenderers.sqlParamScalaType(str), "String")
    assertEquals(FieldRenderers.sqlParamScalaType(instant), "OffsetDateTime")
  }

  test("codecName maps each FieldType to its Skunk codec") {
    assertEquals(FieldRenderers.codecName(str), "text")
    assertEquals(FieldRenderers.codecName(int), "int4")
    assertEquals(FieldRenderers.codecName(bool), "bool")
    assertEquals(FieldRenderers.codecName(instant), "timestamptz")
  }

  test("ddlType maps each FieldType to its Postgres column type") {
    assertEquals(FieldRenderers.ddlType(str), "TEXT")
    assertEquals(FieldRenderers.ddlType(int), "INT")
    assertEquals(FieldRenderers.ddlType(bool), "BOOLEAN")
    assertEquals(FieldRenderers.ddlType(instant), "TIMESTAMPTZ")
  }

  test("sqlWriteExpr passes non-Instant values through unchanged") {
    assertEquals(FieldRenderers.sqlWriteExpr(str, "color"), "color")
  }

  test("sqlWriteExpr converts an Instant to OffsetDateTime for writing") {
    assertEquals(
      FieldRenderers.sqlWriteExpr(instant, "expiresAt"),
      "expiresAt.atOffset(java.time.ZoneOffset.UTC)"
    )
  }

  test("fromSqlToDomain passes non-Instant values through unchanged") {
    assertEquals(FieldRenderers.fromSqlToDomain(str, "color"), "color")
  }

  test(
    "fromSqlToDomain converts a raw OffsetDateTime column value to Instant"
  ) {
    assertEquals(
      FieldRenderers.fromSqlToDomain(instant, "expiresAt"),
      "expiresAt.toInstant"
    )
  }

  test("literalExample quotes a String example and escapes embedded quotes") {
    assertEquals(FieldRenderers.literalExample(str), "\"red\"")
    assertEquals(
      FieldRenderers.literalExample(Field("n", FieldType.StringType, "a\"b")),
      "\"a\\\"b\""
    )
  }

  test("literalExample renders Int/Boolean examples as raw literals") {
    assertEquals(FieldRenderers.literalExample(int), "42")
    assertEquals(FieldRenderers.literalExample(bool), "true")
  }

  test("literalExample wraps an Instant example in Instant.parse") {
    assertEquals(
      FieldRenderers.literalExample(instant),
      """java.time.Instant.parse("2026-01-01T00:00:00Z")"""
    )
  }

  test("snakeCase converts camelCase identifiers to snake_case") {
    assertEquals(FieldRenderers.snakeCase("expiresAt"), "expires_at")
    assertEquals(FieldRenderers.snakeCase("color"), "color")
    assertEquals(FieldRenderers.snakeCase("isFragileItem"), "is_fragile_item")
  }

  private val expectedTags = List(
    "CASE_CLASS_FIELD",
    "CREATE_PARAMS",
    "UPDATE_PARAMS",
    "CONSTRUCT_ARGS",
    "COPY_ARGS",
    "CREATE_CALL_ARGS",
    "UPDATE_CALL_ARGS",
    "RESPONSE_APPLY_ARGS",
    "SQL_CREATE_COLUMN",
    "SQL_INSERT_COLUMNS",
    "SQL_INSERT_PARAMS",
    "SQL_INSERT_TUPLE_TYPE",
    "SQL_INSERT_TUPLE_ARGS",
    "SQL_SELECT_TUPLE_TYPE",
    "SQL_SELECT_COLUMNS",
    "SQL_SELECT_CODEC",
    "SQL_SELECT_CONSTRUCT_ARGS",
    "SQL_SELECT_PATTERN_VARS",
    "SQL_UPDATE_TUPLE_TYPE",
    "SQL_UPDATE_SET",
    "SQL_UPDATE_TUPLE_ARGS",
    "TEST_CREATE_ARGS",
    "TEST_UPDATE_ARGS",
    "TEST_CREATE_REQUEST_ARGS",
    "TEST_UPDATE_REQUEST_ARGS",
    "TEST_MIGRATION_COLUMN",
    "TEST_LIFECYCLE_ASSERT"
  )

  test(
    "cellRenderers covers exactly the 27 anchor tags placed in the g8 template"
  ) {
    assertEquals(FieldRenderers.cellRenderers.keySet, expectedTags.toSet)
    assertEquals(FieldRenderers.cellRenderers.size, 27)
  }

  test("statementModeTags contains only TEST_LIFECYCLE_ASSERT") {
    assertEquals(FieldRenderers.statementModeTags, Set("TEST_LIFECYCLE_ASSERT"))
  }

  test("representative cell renderers produce the expected fragments") {
    assertEquals(
      FieldRenderers.cellRenderers("CASE_CLASS_FIELD")(instant),
      "expiresAt: java.time.Instant"
    )
    assertEquals(
      FieldRenderers.cellRenderers("CREATE_PARAMS")(str),
      "color: String"
    )
    assertEquals(FieldRenderers.cellRenderers("CONSTRUCT_ARGS")(str), "color")
    assertEquals(
      FieldRenderers.cellRenderers("COPY_ARGS")(str),
      "color = color"
    )
    assertEquals(
      FieldRenderers.cellRenderers("CREATE_CALL_ARGS")(str),
      "req.color"
    )
    assertEquals(
      FieldRenderers.cellRenderers("RESPONSE_APPLY_ARGS")(str),
      "entity.color"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_CREATE_COLUMN")(int),
      "weight INT NOT NULL"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_INSERT_COLUMNS")(instant),
      "expires_at"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_INSERT_PARAMS")(bool),
      "$bool"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_SELECT_CODEC")(instant),
      "timestamptz"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_UPDATE_SET")(int),
      "weight = $int4"
    )
    assertEquals(
      FieldRenderers.cellRenderers("TEST_MIGRATION_COLUMN")(instant),
      "\"expires_at\""
    )
    assertEquals(
      FieldRenderers.cellRenderers("TEST_LIFECYCLE_ASSERT")(int),
      "assertEquals(updated.map(_.weight), Some(42))"
    )
  }
}
