package codegen

import munit.FunSuite

class FieldRenderersSuite extends FunSuite {

  private val str =
    Field("color", FieldType.StringType, "red", "Colour of the widget.")
  private val int = Field("weight", FieldType.IntType, "42", "Weight in grams.")
  private val bool =
    Field("fragile", FieldType.BooleanType, "true", "Whether it breaks easily.")
  private val instant =
    Field(
      "expiresAt",
      FieldType.InstantType,
      "2026-01-01T00:00:00Z",
      "When it expires (UTC)."
    )

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
      FieldRenderers.literalExample(
        Field("n", FieldType.StringType, "a\"b", "A name.")
      ),
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
    "TEST_LIFECYCLE_ASSERT",
    "DEFAULT_VALUE_DECLS",
    "DTO_CREATE_FIELD",
    "DTO_UPDATE_FIELD",
    "DTO_RESPONSE_FIELD"
  )

  test(
    "cellRenderers covers exactly the 31 anchor tags placed in the g8 template, and visibilityFilters covers the same set"
  ) {
    assertEquals(FieldRenderers.cellRenderers.keySet, expectedTags.toSet)
    assertEquals(FieldRenderers.cellRenderers.size, 31)
    assertEquals(FieldRenderers.visibilityFilters.keySet, expectedTags.toSet)
  }

  test(
    "statementModeTags contains TEST_LIFECYCLE_ASSERT and DEFAULT_VALUE_DECLS"
  ) {
    assertEquals(
      FieldRenderers.statementModeTags,
      Set("TEST_LIFECYCLE_ASSERT", "DEFAULT_VALUE_DECLS")
    )
  }

  test("capitalize upper-cases just the first letter") {
    assertEquals(FieldRenderers.capitalize("status"), "Status")
    assertEquals(FieldRenderers.capitalize("expiresAt"), "ExpiresAt")
  }

  private val createOnlyItem =
    Field(
      "item",
      FieldType.StringType,
      "widget",
      "What was ordered.",
      Visibility.CreateOnly
    )
  private val createAndUpdateQty =
    Field(
      "quantity",
      FieldType.IntType,
      "5",
      "Units ordered.",
      Visibility.CreateAndUpdate
    )
  private val serverDefaultedStatus = Field(
    "status",
    FieldType.StringType,
    "shipped",
    "Where it is in its lifecycle.",
    Visibility.ServerDefaulted,
    Some("created")
  )

  test("visibilityFilters: create-side tags exclude server-defaulted fields") {
    List(
      "CREATE_PARAMS",
      "CREATE_CALL_ARGS",
      "TEST_CREATE_ARGS",
      "TEST_CREATE_REQUEST_ARGS",
      "DTO_CREATE_FIELD"
    )
      .foreach { tag =>
        val filtered =
          List(createOnlyItem, createAndUpdateQty, serverDefaultedStatus)
            .filter(FieldRenderers.visibilityFilters(tag))
        assertEquals(
          filtered,
          List(createOnlyItem, createAndUpdateQty),
          s"tag: $tag"
        )
      }
  }

  test("visibilityFilters: update-side tags exclude create-only fields") {
    List(
      "UPDATE_PARAMS",
      "UPDATE_CALL_ARGS",
      "COPY_ARGS",
      "SQL_UPDATE_TUPLE_TYPE",
      "SQL_UPDATE_SET",
      "SQL_UPDATE_TUPLE_ARGS",
      "TEST_UPDATE_ARGS",
      "TEST_UPDATE_REQUEST_ARGS",
      "DTO_UPDATE_FIELD"
    ).foreach { tag =>
      val filtered =
        List(createOnlyItem, createAndUpdateQty, serverDefaultedStatus)
          .filter(FieldRenderers.visibilityFilters(tag))
      assertEquals(
        filtered,
        List(createAndUpdateQty, serverDefaultedStatus),
        s"tag: $tag"
      )
    }
  }

  test(
    "visibilityFilters: most tags render every field regardless of visibility"
  ) {
    List(
      "CASE_CLASS_FIELD",
      "CONSTRUCT_ARGS",
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
      "TEST_MIGRATION_COLUMN",
      "TEST_LIFECYCLE_ASSERT",
      "DTO_RESPONSE_FIELD"
    ).foreach { tag =>
      val filtered =
        List(createOnlyItem, createAndUpdateQty, serverDefaultedStatus)
          .filter(FieldRenderers.visibilityFilters(tag))
      assertEquals(
        filtered,
        List(createOnlyItem, createAndUpdateQty, serverDefaultedStatus),
        s"tag: $tag"
      )
    }
  }

  test(
    "visibilityFilters: DEFAULT_VALUE_DECLS renders only server-defaulted fields"
  ) {
    val filtered =
      List(createOnlyItem, createAndUpdateQty, serverDefaultedStatus)
        .filter(FieldRenderers.visibilityFilters("DEFAULT_VALUE_DECLS"))
    assertEquals(filtered, List(serverDefaultedStatus))
  }

  test(
    "CONSTRUCT_ARGS and SQL_INSERT_TUPLE_ARGS reference a default constant for server-defaulted fields, the param name otherwise"
  ) {
    assertEquals(
      FieldRenderers.cellRenderers("CONSTRUCT_ARGS")(createAndUpdateQty),
      "quantity"
    )
    assertEquals(
      FieldRenderers.cellRenderers("CONSTRUCT_ARGS")(serverDefaultedStatus),
      "defaultStatus"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_INSERT_TUPLE_ARGS")(createAndUpdateQty),
      "quantity"
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_INSERT_TUPLE_ARGS")(
        serverDefaultedStatus
      ),
      "defaultStatus"
    )
    val serverDefaultedInstant = Field(
      "expiresAt",
      FieldType.InstantType,
      "2026-01-01T00:00:00Z",
      "When it expires (UTC).",
      Visibility.ServerDefaulted,
      Some("2030-01-01T00:00:00Z")
    )
    assertEquals(
      FieldRenderers.cellRenderers("SQL_INSERT_TUPLE_ARGS")(
        serverDefaultedInstant
      ),
      "defaultExpiresAt.atOffset(java.time.ZoneOffset.UTC)"
    )
  }

  test(
    "DEFAULT_VALUE_DECLS renders a private val declaration using the field's default literal"
  ) {
    assertEquals(
      FieldRenderers.cellRenderers("DEFAULT_VALUE_DECLS")(
        serverDefaultedStatus
      ),
      "private val defaultStatus = \"created\""
    )
    val serverDefaultedInt = Field(
      "retries",
      FieldType.IntType,
      "3",
      "Delivery attempts so far.",
      Visibility.ServerDefaulted,
      Some("0")
    )
    assertEquals(
      FieldRenderers.cellRenderers("DEFAULT_VALUE_DECLS")(serverDefaultedInt),
      "private val defaultRetries = 0"
    )
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

  test(
    "DTO field cells annotate the field with its description and an encoded example"
  ) {
    List("DTO_CREATE_FIELD", "DTO_UPDATE_FIELD", "DTO_RESPONSE_FIELD")
      .foreach { tag =>
        assertEquals(
          FieldRenderers.cellRenderers(tag)(str),
          "@description(\"Colour of the widget.\") @encodedExample(\"red\") color: String",
          s"tag: $tag"
        )
      }
    assertEquals(
      FieldRenderers.cellRenderers("DTO_RESPONSE_FIELD")(int),
      "@description(\"Weight in grams.\") @encodedExample(42) weight: Int"
    )
    assertEquals(
      FieldRenderers.cellRenderers("DTO_RESPONSE_FIELD")(bool),
      "@description(\"Whether it breaks easily.\") @encodedExample(true) fragile: Boolean"
    )
  }

  test("DTO field cells encode an Instant example as its ISO-8601 string") {
    assertEquals(
      FieldRenderers.cellRenderers("DTO_RESPONSE_FIELD")(instant),
      "@description(\"When it expires (UTC).\") @encodedExample(\"2026-01-01T00:00:00Z\") expiresAt: java.time.Instant"
    )
  }

  test("DTO field cells escape quotes in the description") {
    assertEquals(
      FieldRenderers.cellRenderers("DTO_CREATE_FIELD")(
        Field("n", FieldType.StringType, "x", "A \"name\".")
      ),
      "@description(\"A \\\"name\\\".\") @encodedExample(\"x\") n: String"
    )
  }
}
