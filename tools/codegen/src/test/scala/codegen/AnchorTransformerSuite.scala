package codegen

import munit.FunSuite

class AnchorTransformerSuite extends FunSuite {

  private val fields = List(
    Field("color", FieldType.StringType, "red"),
    Field("weight", FieldType.IntType, "42")
  )

  test(
    "leading-comma inline: marker sits directly after the last item, no separator"
  ) {
    val input =
      "  def create(item: String, quantity: Int/* codegen:fields:CREATE_PARAMS */): F[X]"
    val expected =
      "  def create(item: String, quantity: Int, color: String, weight: Int): F[X]"
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test(
    "trailing-comma inline: marker sits after an existing trailing comma+space"
  ) {
    val input =
      "    (String, Int, String, /* codegen:fields:SQL_SELECT_TUPLE_TYPE */OffsetDateTime, OffsetDateTime)"
    val expected =
      "    (String, Int, String, String, Int, OffsetDateTime, OffsetDateTime)"
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test(
    "trailing-combinator inline: marker sits after a Skunk '*:' combinator chain"
  ) {
    val input =
      "    .query(text *: int4 *: text *: /* codegen:fields:SQL_SELECT_CODEC */timestamptz *: timestamptz)"
    val expected =
      "    .query(text *: int4 *: text *: text *: int4 *: timestamptz *: timestamptz)"
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test(
    "own-line comma-list: '//' comment alone on its line, preceding line already has a trailing comma"
  ) {
    val input =
      """|final case class Widget(
         |    id: String,
         |    status: String,
         |    // codegen:fields:CASE_CLASS_FIELD
         |    createdAt: java.time.Instant
         |)""".stripMargin
    val expected =
      """|final case class Widget(
         |    id: String,
         |    status: String,
         |    color: String,
         |    weight: Int,
         |    createdAt: java.time.Instant
         |)""".stripMargin
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test(
    "own-line comma-list: preceding line is missing its trailing comma, and gets one appended"
  ) {
    val input =
      """|        def update(
         |            id: String,
         |            status: String
         |            /* codegen:fields:UPDATE_PARAMS */
         |        ): F[Option[Widget]] =""".stripMargin
    val expected =
      """|        def update(
         |            id: String,
         |            status: String,
         |            color: String,
         |            weight: Int
         |        ): F[Option[Widget]] =""".stripMargin
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test("own-line statement mode: no trailing commas, no preceding-line fixup") {
    val input =
      """|            assertEquals(read1, Some(created))
         |            // codegen:fields:TEST_LIFECYCLE_ASSERT
         |            assertEquals(updated.map(_.quantity), Some(9))""".stripMargin
    val expected =
      """|            assertEquals(read1, Some(created))
         |            assertEquals(updated.map(_.color), Some("red"))
         |            assertEquals(updated.map(_.weight), Some(42))
         |            assertEquals(updated.map(_.quantity), Some(9))""".stripMargin
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test(
    "SQL line-comment ('--') own-line anchor, as used in the migration file"
  ) {
    val input =
      """|    status TEXT NOT NULL DEFAULT 'created',
         |    -- codegen:fields:SQL_CREATE_COLUMN
         |    created_at TIMESTAMPTZ NOT NULL DEFAULT now()""".stripMargin
    val expected =
      """|    status TEXT NOT NULL DEFAULT 'created',
         |    color TEXT NOT NULL,
         |    weight INT NOT NULL,
         |    created_at TIMESTAMPTZ NOT NULL DEFAULT now()""".stripMargin
    assertEquals(AnchorTransformer.transform(input, fields), Right(expected))
  }

  test("lines without an anchor are left unchanged") {
    val input = "  def get(id: String): F[Option[Widget]]"
    assertEquals(AnchorTransformer.transform(input, fields), Right(input))
  }

  test("unknown anchor tag is reported as a Left, not thrown") {
    val input =
      "  def create(item: String/* codegen:fields:NOT_A_REAL_TAG */): F[X]"
    assertEquals(
      AnchorTransformer.transform(input, fields),
      Left("Unknown codegen:fields anchor tag 'NOT_A_REAL_TAG'")
    )
  }

  test("a full multi-anchor file is transformed consistently end to end") {
    val input =
      """|final case class Widget(
         |    status: String,
         |    // codegen:fields:CASE_CLASS_FIELD
         |    createdAt: java.time.Instant
         |)
         |
         |def create(item: String, quantity: Int/* codegen:fields:CREATE_PARAMS */): F[Widget]""".stripMargin
    val result = AnchorTransformer.transform(input, fields)
    assert(result.isRight, s"expected Right, got: $result")
    val text = result.toOption.get
    assert(
      clue(text).contains("color: String,\n    weight: Int,\n    createdAt")
    )
    assert(
      clue(text).contains(
        "create(item: String, quantity: Int, color: String, weight: Int): F[Widget]"
      )
    )
  }
}
