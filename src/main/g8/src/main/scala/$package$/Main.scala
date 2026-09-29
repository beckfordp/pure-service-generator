package $package$

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.docs.Docs
import purerest.logging.Logging
import purerest.metrics.{Metrics, ServerMetrics}
import purerest.tracing.{ServerTracing, Tracing}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    for {
      config <- $domain_name;format="cap"$ServiceConfig.load[IO]
      port <- IO.fromOption(Port.fromInt(config.port))(
        new IllegalArgumentException(
          s"Invalid $domain_name$-service port: \${config.port}"
        )
      )
      _ <- Migrations.run[IO](config.postgres)
      _ <- Tracing.console[IO](config.serviceName).use { tracer =>
        Metrics.oteljava[IO](config.serviceName, config.metricsPort).use {
          meter =>
            for {
              logger <- Logging.create[IO](tracer, config.serviceName)
              _ <- logger.info(
                Map(
                  "port" -> config.port.toString,
                  "metrics_port" -> config.metricsPort.toString
                )
              )("$domain_name$-service starting")
              _ <- $domain_name;format="cap"$Store.postgres[IO](config.postgres, meter).use {
                store =>
                  val docsRoutes = Docs.routes[IO](
                    "$domain_name;format="cap"$ Service",
                    "1.0",
                    List(
                      $domain_name;format="cap"$Routes.serverEndpoint[IO](store, logger),
                      $domain_name;format="cap"$Routes.get$domain_name;format="cap"$ServerEndpoint[IO](store, logger),
                      $domain_name;format="cap"$Routes.update$domain_name;format="cap"$ServerEndpoint[IO](store, logger),
                      $domain_name;format="cap"$Routes.replace$domain_name;format="cap"$ServerEndpoint[IO](store, logger),
                      $domain_name;format="cap"$Routes.delete$domain_name;format="cap"$ServerEndpoint[IO](store, logger),
                      HealthRoutes.healthServerEndpoint[IO],
                      HealthRoutes.readyServerEndpoint[IO](store)
                    )
                  )
                  val tracedRoutes =
                    ServerTracing.middleware(tracer)(docsRoutes)
                  val routes =
                    ServerMetrics.middleware[IO](meter)(tracedRoutes)
                  EmberServerBuilder
                    .default[IO]
                    .withHost(host"0.0.0.0")
                    .withPort(port)
                    .withHttpApp(routes.orNotFound)
                    .build
                    .useForever
              }
            } yield ()
        }
      }
    } yield ()
}
