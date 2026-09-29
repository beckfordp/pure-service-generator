package $package$

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import purerest.docs.Docs

class $domain_name;format="cap"$DocsSuite extends CatsEffectSuite {

  test(
    "the tapir-described endpoint is served and documented via purerest.docs"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      endpoint = $domain_name;format="cap"$Routes.serverEndpoint[IO](store, NoOpLogger[IO])
      routes = Docs.routes[IO]("$domain_name;format="cap"$ Service", "1.0", List(endpoint))
      request = Request[IO](Method.POST, uri"/$domain_name$s")
        .withEntity(Create$domain_name;format="cap"$Request("widget", 4/* codegen:fields:TEST_CREATE_REQUEST_ARGS */))
      response <- routes.orNotFound.run(request)
      entity <- response.as[$domain_name;format="cap"$Response]
      docsResponse <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/docs/docs.yaml")
      )
      docsBody <- docsResponse.bodyText.compile.string
    } yield {
      assertEquals(response.status, Status.Created)
      assertEquals(entity.item, "widget")
      assertEquals(entity.quantity, 4)
      assertEquals(docsResponse.status, Status.Ok)
      assert(clue(docsBody).contains("/$domain_name$s"))
    }
  }
}
