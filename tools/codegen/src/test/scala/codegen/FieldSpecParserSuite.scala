// SPDX-License-Identifier: MIT

package codegen

import munit.FunSuite

class FieldSpecParserSuite extends FunSuite {

  private val yaml =
    """fields:
      |  - name: color
      |    type: String
      |    example: "red"
      |    description: "Colour of the widget."
      |  - name: weight
      |    type: Int
      |    example: "42"
      |    description: "Weight in grams."
      |  - name: fragile
      |    type: Boolean
      |    example: "true"
      |    description: "Whether it breaks easily."
      |  - name: expiresAt
      |    type: Instant
      |    example: "2026-01-01T00:00:00Z"
      |    description: "When it expires (UTC)."
      |""".stripMargin

  test("parses a well-formed field-spec YAML document into a FieldSpec") {
    val result = FieldSpecParser.parse(yaml)
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field(
              "color",
              FieldType.StringType,
              "red",
              "Colour of the widget."
            ),
            Field("weight", FieldType.IntType, "42", "Weight in grams."),
            Field(
              "fragile",
              FieldType.BooleanType,
              "true",
              "Whether it breaks easily."
            ),
            Field(
              "expiresAt",
              FieldType.InstantType,
              "2026-01-01T00:00:00Z",
              "When it expires (UTC)."
            )
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
        |    description: "Colour of the widget."
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
        |    description: "Weight in grams."
        |""".stripMargin,
      """fields:
        |  - name: fragile
        |    type: Boolean
        |    example: "yes"
        |    description: "Whether it breaks easily."
        |""".stripMargin,
      """fields:
        |  - name: expiresAt
        |    type: Instant
        |    example: "not-a-timestamp"
        |    description: "When it expires (UTC)."
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
        |    description: "Colour of the widget."
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
        |    description: "Colour of the widget."
        |""".stripMargin
    )
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field("color", FieldType.StringType, "red", "Colour of the widget.")
          )
        )
      )
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
        |    description: "What was ordered."
        |    visibility: create-only
        |""".stripMargin
    )
    assertEquals(
      result,
      Right(
        FieldSpec(
          List(
            Field(
              "item",
              FieldType.StringType,
              "widget",
              "What was ordered.",
              Visibility.CreateOnly
            )
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
        |    description: "Where it is in its lifecycle."
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
              "Where it is in its lifecycle.",
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
        |    description: "Where it is in its lifecycle."
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
        |    description: "Units ordered."
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
        |    description: "Colour of the widget."
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
        |    description: "Weight in grams."
        |    visibility: server-defaulted
        |    default: "not-a-number"
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }

  test("rejects a field missing its description") {
    val result = FieldSpecParser.parse(
      """fields:
        |  - name: color
        |    type: String
        |    example: "red"
        |""".stripMargin
    )
    assert(result.isLeft, s"expected a Left, got: $result")
  }
}
