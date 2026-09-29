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
}
