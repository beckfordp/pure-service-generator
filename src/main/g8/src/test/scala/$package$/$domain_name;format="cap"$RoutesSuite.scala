package $package$

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger.{
  ERROR,
  INFO,
  WARN
}
import purerest.tracing.{ServerTracing, Tracing}

class $domain_name;format="cap"$RoutesSuite extends CatsEffectSuite {

  private def failingStore(error: Throwable): $domain_name;format="cap"$Store[IO] =
    new $domain_name;format="cap"$Store[IO] {
      def create(item: String, quantity: Int): IO[$domain_name;format="cap"$] =
        IO.raiseError(error)
      def get(id: String): IO[Option[$domain_name;format="cap"$]] = IO.pure(None)
      def update(
          id: String,
          quantity: Int,
          status: String
      ): IO[Option[$domain_name;format="cap"$]] =
        IO.raiseError(error)
      def delete(id: String): IO[Boolean] = IO.raiseError(error)
      def ping: IO[Boolean] = IO.raiseError(error)
    }

  test("POST /$domain_name$s returns 201 with the created entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      request = Request[IO](Method.POST, uri"/$domain_name$s")
        .withEntity(Create$domain_name;format="cap"$Request("widget", 4))
      response <- routes.orNotFound.run(request)
      entity <- response.as[$domain_name;format="cap"$Response]
    } yield {
      assertEquals(response.status, Status.Created)
      assertEquals(entity.item, "widget")
      assertEquals(entity.quantity, 4)
      assertEquals(entity.status, "created")
    }
  }

  test("GET /$domain_name$s/{id} returns 200 with the persisted entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      getResponse <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/$domain_name$s" / created.id)
      )
      fetched <- getResponse.as[$domain_name;format="cap"$Response]
    } yield {
      assertEquals(getResponse.status, Status.Ok)
      assertEquals(fetched, created)
    }
  }

  test(
    "GET /$domain_name$s/{id} returns 404 with a JSON error body for an unknown id"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      response <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/$domain_name$s" / "unknown-id")
      )
      body <- response.as[io.circe.Json]
    } yield {
      assertEquals(response.status, Status.NotFound)
      assert(
        body.asObject.exists(_.contains("error")),
        s"expected a JSON error body, got: \$body"
      )
    }
  }

  test(
    "POST /$domain_name$s logs a received-request line and a completed line with structured context"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      request = Request[IO](Method.POST, uri"/$domain_name$s")
        .withEntity(Create$domain_name;format="cap"$Request("widget", 4))
      response <- routes.orNotFound.run(request)
      entity <- response.as[$domain_name;format="cap"$Response]
      logged <- testLogger.logged
    } yield {
      val infos = logged.collect { case m: INFO => m }
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("received") &&
            m.ctx.get("method").contains("POST") &&
            m.ctx.get("item").contains("widget")
        ),
        s"expected a received-request INFO line with method/item context, got: \$infos"
      )
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("completed") &&
            m.ctx.get("$domain_name$_id").contains(entity.id)
        ),
        s"expected a completed INFO line with $domain_name$_id context, got: \$infos"
      )
    }
  }

  test(
    "POST /$domain_name$s logs an ERROR with context when persisting the entity fails"
  ) {
    val boom = new RuntimeException("boom")
    for {
      testLogger <- IO.pure(StructuredTestingLogger.impl[IO]())
      routes = $domain_name;format="cap"$Routes.routes[IO](failingStore(boom), testLogger)
      request = Request[IO](Method.POST, uri"/$domain_name$s")
        .withEntity(Create$domain_name;format="cap"$Request("widget", 4))
      response <- routes.orNotFound.run(request)
      logged <- testLogger.logged
    } yield {
      assertEquals(response.status, Status.InternalServerError)
      val errors = logged.collect { case m: ERROR => m }
      assert(
        errors.exists(m =>
          m.ctx.get("item").contains("widget") && m.throwOpt.contains(boom)
        ),
        s"expected an ERROR line with item context and the raised throwable, got: \$errors"
      )
    }
  }

  test(
    "GET /$domain_name$s/{id} logs a received-request line and a completed line for a found entity"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      _ <- testLogger.logged // drain POST's own log lines before the GET
      getResponse <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/$domain_name$s" / created.id)
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(getResponse.status, Status.Ok)
      val infos = logged.collect { case m: INFO => m }
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("received") &&
            m.ctx.get("method").contains("GET") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a received-request INFO line with method/id context, got: \$infos"
      )
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("completed") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a completed INFO line with id context, got: \$infos"
      )
    }
  }

  test(
    "GET /$domain_name$s/{id} logs a WARN for an unknown id"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      response <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/$domain_name$s" / "unknown-id")
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(response.status, Status.NotFound)
      val warns = logged.collect { case m: WARN => m }
      assert(
        warns.exists(m =>
          m.message.toLowerCase.contains("not found") &&
            m.ctx.get("$domain_name$_id").contains("unknown-id")
        ),
        s"expected a 'not found' WARN line with id context, got: \$warns"
      )
    }
  }

  test("PATCH /$domain_name$s/{id} returns 200 with the updated entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      patchResponse <- routes.orNotFound.run(
        Request[IO](Method.PATCH, uri"/$domain_name$s" / created.id)
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      updated <- patchResponse.as[$domain_name;format="cap"$Response]
    } yield {
      assertEquals(patchResponse.status, Status.Ok)
      assertEquals(updated.id, created.id)
      assertEquals(updated.quantity, 9)
      assertEquals(updated.status, "shipped")
    }
  }

  test(
    "PATCH /$domain_name$s/{id} returns 404 with a JSON error body for an unknown id"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      response <- routes.orNotFound.run(
        Request[IO](Method.PATCH, uri"/$domain_name$s" / "unknown-id")
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      body <- response.as[io.circe.Json]
    } yield {
      assertEquals(response.status, Status.NotFound)
      assert(
        body.asObject.exists(_.contains("error")),
        s"expected a JSON error body, got: \$body"
      )
    }
  }

  test(
    "PATCH /$domain_name$s/{id} logs a received-request line and a completed line for a found entity"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      _ <- testLogger.logged // drain POST's own log lines before the PATCH
      patchResponse <- routes.orNotFound.run(
        Request[IO](Method.PATCH, uri"/$domain_name$s" / created.id)
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(patchResponse.status, Status.Ok)
      val infos = logged.collect { case m: INFO => m }
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("received") &&
            m.ctx.get("method").contains("PATCH") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a received-request INFO line with method/id context, got: \$infos"
      )
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("completed") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a completed INFO line with id context, got: \$infos"
      )
    }
  }

  test("PATCH /$domain_name$s/{id} logs a WARN for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      response <- routes.orNotFound.run(
        Request[IO](Method.PATCH, uri"/$domain_name$s" / "unknown-id")
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(response.status, Status.NotFound)
      val warns = logged.collect { case m: WARN => m }
      assert(
        warns.exists(m =>
          m.message.toLowerCase.contains("not found") &&
            m.ctx.get("$domain_name$_id").contains("unknown-id")
        ),
        s"expected a 'not found' WARN line with id context, got: \$warns"
      )
    }
  }

  test(
    "DELETE /$domain_name$s/{id} returns 204, and a subsequent GET returns 404"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      deleteResponse <- routes.orNotFound.run(
        Request[IO](Method.DELETE, uri"/$domain_name$s" / created.id)
      )
      getResponse <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/$domain_name$s" / created.id)
      )
    } yield {
      assertEquals(deleteResponse.status, Status.NoContent)
      assertEquals(getResponse.status, Status.NotFound)
    }
  }

  test(
    "DELETE /$domain_name$s/{id} returns 404 with a JSON error body for an unknown id"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      response <- routes.orNotFound.run(
        Request[IO](Method.DELETE, uri"/$domain_name$s" / "unknown-id")
      )
      body <- response.as[io.circe.Json]
    } yield {
      assertEquals(response.status, Status.NotFound)
      assert(
        body.asObject.exists(_.contains("error")),
        s"expected a JSON error body, got: \$body"
      )
    }
  }

  test(
    "DELETE /$domain_name$s/{id} logs a received-request line and a completed line for a found entity"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      _ <- testLogger.logged // drain POST's own log lines before the DELETE
      deleteResponse <- routes.orNotFound.run(
        Request[IO](Method.DELETE, uri"/$domain_name$s" / created.id)
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(deleteResponse.status, Status.NoContent)
      val infos = logged.collect { case m: INFO => m }
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("received") &&
            m.ctx.get("method").contains("DELETE") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a received-request INFO line with method/id context, got: \$infos"
      )
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("completed") &&
            m.ctx.get("$domain_name$_id").contains(created.id)
        ),
        s"expected a completed INFO line with id context, got: \$infos"
      )
    }
  }

  test("DELETE /$domain_name$s/{id} logs a WARN for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = $domain_name;format="cap"$Routes.routes[IO](store, testLogger)
      response <- routes.orNotFound.run(
        Request[IO](Method.DELETE, uri"/$domain_name$s" / "unknown-id")
      )
      logged <- testLogger.logged
    } yield {
      assertEquals(response.status, Status.NotFound)
      val warns = logged.collect { case m: WARN => m }
      assert(
        warns.exists(m =>
          m.message.toLowerCase.contains("not found") &&
            m.ctx.get("$domain_name$_id").contains("unknown-id")
        ),
        s"expected a 'not found' WARN line with id context, got: \$warns"
      )
    }
  }

  test("PUT /$domain_name$s/{id} returns 200 with the replaced entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/$domain_name$s").withEntity(
          Create$domain_name;format="cap"$Request("widget", 4)
        )
      )
      created <- postResponse.as[$domain_name;format="cap"$Response]
      putResponse <- routes.orNotFound.run(
        Request[IO](Method.PUT, uri"/$domain_name$s" / created.id)
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      replaced <- putResponse.as[$domain_name;format="cap"$Response]
    } yield {
      assertEquals(putResponse.status, Status.Ok)
      assertEquals(replaced.id, created.id)
      assertEquals(replaced.quantity, 9)
      assertEquals(replaced.status, "shipped")
    }
  }

  test(
    "PUT /$domain_name$s/{id} returns 404 with a JSON error body for an unknown id"
  ) {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      routes = $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
      response <- routes.orNotFound.run(
        Request[IO](Method.PUT, uri"/$domain_name$s" / "unknown-id")
          .withEntity(Update$domain_name;format="cap"$Request(9, "shipped"))
      )
      body <- response.as[io.circe.Json]
    } yield {
      assertEquals(response.status, Status.NotFound)
      assert(
        body.asObject.exists(_.contains("error")),
        s"expected a JSON error body, got: \$body"
      )
    }
  }

  test(
    "wrapped routes (with tracing middleware) record a span for a handled request"
  ) {
    Tracing.test[IO]("$domain_name$-service-test").use { testTracer =>
      for {
        store <- $domain_name;format="cap"$Store.inMemory[IO]
        routes = ServerTracing.middleware(testTracer.tracer)(
          $domain_name;format="cap"$Routes.routes[IO](store, NoOpLogger[IO])
        )
        request = Request[IO](Method.POST, uri"/$domain_name$s")
          .withEntity(Create$domain_name;format="cap"$Request("widget", 4))
        response <- routes.orNotFound.run(request)
        spans <- testTracer.finishedSpans
      } yield {
        assertEquals(response.status, Status.Created)
        assertEquals(spans.map(_.getName), List("POST /$domain_name$s"))
      }
    }
  }
}
