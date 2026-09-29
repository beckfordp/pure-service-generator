package $package$

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}

class HealthRoutesSuite extends CatsEffectSuite {

  private val readyStore: $domain_name;format="cap"$Store[IO] =
    new $domain_name;format="cap"$Store[IO] {
      def create(/* codegen:fields:CREATE_PARAMS */): IO[$domain_name;format="cap"$] =
        IO.raiseError(new NotImplementedError())
      def get(id: String): IO[Option[$domain_name;format="cap"$]] = IO.pure(None)
      def update(
          id: String
          /* codegen:fields:UPDATE_PARAMS */
      ): IO[Option[$domain_name;format="cap"$]] = IO.pure(None)
      def delete(id: String): IO[Boolean] = IO.pure(false)
      def ping: IO[Boolean] = IO.pure(true)
    }

  private val notReadyStore: $domain_name;format="cap"$Store[IO] =
    new $domain_name;format="cap"$Store[IO] {
      def create(/* codegen:fields:CREATE_PARAMS */): IO[$domain_name;format="cap"$] =
        IO.raiseError(new NotImplementedError())
      def get(id: String): IO[Option[$domain_name;format="cap"$]] = IO.pure(None)
      def update(
          id: String
          /* codegen:fields:UPDATE_PARAMS */
      ): IO[Option[$domain_name;format="cap"$]] = IO.pure(None)
      def delete(id: String): IO[Boolean] = IO.pure(false)
      def ping: IO[Boolean] = IO.pure(false)
    }

  test("GET /health returns 200") {
    val routes = HealthRoutes.routes[IO](readyStore)
    for {
      response <- routes.orNotFound.run(Request[IO](Method.GET, uri"/health"))
    } yield assertEquals(response.status, Status.Ok)
  }

  test("GET /health/ready returns 200 when the store is ready") {
    val routes = HealthRoutes.routes[IO](readyStore)
    for {
      response <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/health/ready")
      )
    } yield assertEquals(response.status, Status.Ok)
  }

  test("GET /health/ready returns 503 when the store is not ready") {
    val routes = HealthRoutes.routes[IO](notReadyStore)
    for {
      response <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/health/ready")
      )
    } yield assertEquals(response.status, Status.ServiceUnavailable)
  }
}
