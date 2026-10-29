package $package$

import $package$.domain.$domain_name;format="cap"$Id
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class $domain_name;format="cap"$IdSuite extends ScalaCheckSuite {

  test("parse accepts a canonical UUID and render round-trips it") {
    val raw = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    assertEquals(
      $domain_name;format="cap"$Id.parse(raw).map($domain_name;format="cap"$Id.render),
      Right(raw)
    )
  }

  test("parse rejects a malformed id") {
    assert($domain_name;format="cap"$Id.parse("not-a-uuid").isLeft)
  }

  property("parse then render is the identity for any UUID") {
    forAll(Gen.uuid) { uuid =>
      val raw = uuid.toString
      assertEquals(
        $domain_name;format="cap"$Id.parse(raw).map($domain_name;format="cap"$Id.render),
        Right(raw)
      )
    }
  }
}
