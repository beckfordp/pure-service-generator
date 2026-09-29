package codegen

import munit.FunSuite

class FieldSpecParserSuite extends FunSuite {

  private val yaml =
    """fields:
      |  - name: color
      |    type: String
      |    example: "red"
      |  - name: weight
      |    type: Int
      |    example: "42"
      |  - name: fragile
      |    type: Boolean
      |    example: "true"
      |  - name: expiresAt
      |    type: Instant
      |    example: "2026-01-01T00:00:00Z"
      |""".stripMargin

  test("parses a well-formed field-spec YAML document into a FieldSpec") {
    val result = FieldSpecParser.parse(yaml)
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field("color", FieldType.StringType, "red"),
            Field("weight", FieldType.IntType, "42"),
            Field("fragile", FieldType.BooleanType, "true"),
            Field("expiresAt", FieldType.InstantType, "2026-01-01T00:00:00Z")
          )
        )
      )
    )
  }

  test("rejects an empty fields list") {
    val result = FieldSpecParser.parse("fields: []")
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects an unknown field type") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: color
        |    type: Float
        |    example: "1.5"
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects malformed YAML") {
    val result = FieldSpecParser.parse("not: [valid, yaml")
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects an example that doesn't parse as its declared type's literal") {
    val badExamples = List(
      """fields:
        |  - name: weight
        |    type: Int
        |    example: "not-a-number"
        |""".stripMargin,
      """fields:
        |  - name: fragile
        |    type: Boolean
        |    example: "yes"
        |""".stripMargin,
      """fields:
        |  - name: expiresAt
        |    type: Instant
        |    example: "not-a-timestamp"
        |""".stripMargin
    )
    badExamples.foreach { yaml =>
      val result = FieldSpecParser.parse(yaml)
      assert(result.isLeft, s"expected a Left for: $yaml, got: $result")
    }
  }

  test(
    "accepts a String example of any shape, since String has no narrower literal form"
  ) {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: color
        |    type: String
        |    example: "anything at all, even 42"
        |""".stripMargin
    )
    assert(result.isRight, s"expected a Right, got: $result")
  }

  test(
    "defaults visibility to CreateAndUpdate and default to None when omitted"
  ) {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: color
        |    type: String
        |    example: "red"
        |""".stripMargin
    )
    assertEquals(
      result,
      Right(FieldSpec(List(Field("color", FieldType.StringType, "red"))))
    )
    assertEquals(
      result.toOption.get.fields.head.visibility,
      Visibility.CreateAndUpdate
    )
    assertEquals(result.toOption.get.fields.head.default, None)
  }

  test("parses an explicit create-only visibility") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: item
        |    type: String
        |    example: "widget"
        |    visibility: create-only
        |""".stripMargin
    )
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field("item", FieldType.StringType, "widget", Visibility.CreateOnly)
          )
        )
      )
    )
  }

  test("parses a server-defaulted visibility with its required default") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: status
        |    type: String
        |    example: "shipped"
        |    visibility: server-defaulted
        |    default: "created"
        |""".stripMargin
    )
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field(
              "status",
              FieldType.StringType,
              "shipped",
              Visibility.ServerDefaulted,
              Some("created")
            )
          )
        )
      )
    )
  }

  test("rejects a server-defaulted field missing its default") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: status
        |    type: String
        |    example: "shipped"
        |    visibility: server-defaulted
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects a default key on a non-server-defaulted field") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: quantity
        |    type: Int
        |    example: "5"
        |    default: "1"
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects an unknown visibility value") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: color
        |    type: String
        |    example: "red"
        |    visibility: read-only
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects a default that doesn't parse as its declared type's literal") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: weight
        |    type: Int
        |    example: "5"
        |    visibility: server-defaulted
        |    default: "not-a-number"
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }
}
