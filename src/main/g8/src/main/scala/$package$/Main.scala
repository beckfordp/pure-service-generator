package $package$

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.docs.Docs
import purerest.metrics.ServerMetrics
import purerest.tracing.ServerTracing

/** Acquires every dependency this service needs, builds the routes, and serves
  * them — a single flat `Resource` for-comprehension, one `<-` per dependency.
  * Every dependency that isn't already a `Resource` is acquired via a
  * `.resource`/`.loadValidated` method on its own owning type
  * ($domain_name;format="cap"$ServiceConfig, Migrations, Telemetry), so none
  * of their internals (PureConfig, Flyway, OpenTelemetry) leak into this file.
  * The two exceptions: `EmberServerBuilder` is already `Resource`-returning
  * (http4s' own type, no wrapper needed), and the startup log line stays an
  * explicit `Resource.eval` here since logging isn't itself a dependency. See
  * `docs/writing-main.md` for the full rationale and order-service for a
  * fuller worked example once a service grows optional dependencies.
  */
object Main extends IOApp.Simple {

  val run: IO[Unit] = (for {
    (config, port) <- Resource.eval(
      $domain_name;format="cap"$ServiceConfig.loadValidated[IO]
    )
    _ <- Migrations.resource[IO](config.postgres)
    telemetry <- Telemetry.resource[IO](config.serviceName, config.metricsPort)
    _ <- Resource.eval(
      telemetry.logger.info(
        Map(
          "port" -> config.port.toString,
          "metrics_port" -> config.metricsPort.toString
        )
      )("$domain_name$-service starting")
    )
    store <- $domain_name;format="cap"$Store.postgres[IO](config.postgres, telemetry.meter)
    docsRoutes = Docs.routes[IO](
      "$domain_name;format="cap"$ Service",
      "1.0",
      List(
        $domain_name;format="cap"$Routes.serverEndpoint[IO](store, telemetry.logger),
        $domain_name;format="cap"$Routes.get$domain_name;format="cap"$ServerEndpoint[IO](
          store,
          telemetry.logger
        ),
        $domain_name;format="cap"$Routes.update$domain_name;format="cap"$ServerEndpoint[IO](
          store,
          telemetry.logger
        ),
        $domain_name;format="cap"$Routes.replace$domain_name;format="cap"$ServerEndpoint[IO](
          store,
          telemetry.logger
        ),
        $domain_name;format="cap"$Routes.delete$domain_name;format="cap"$ServerEndpoint[IO](
          store,
          telemetry.logger
        ),
        HealthRoutes.healthServerEndpoint[IO],
        HealthRoutes.readyServerEndpoint[IO](store)
      )
    )
    tracedRoutes = ServerTracing.middleware(telemetry.tracer)(docsRoutes)
    meteredRoutes = ServerMetrics.middleware[IO](telemetry.meter)(tracedRoutes)
    routes = Cors.middleware[IO](meteredRoutes)
    _ <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port)
      .withHttpApp(routes.orNotFound)
      .build
  } yield ()).useForever
}
