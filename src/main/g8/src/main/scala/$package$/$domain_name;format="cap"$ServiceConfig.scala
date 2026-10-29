package $package$

import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s.Port
import pureconfig.{ConfigReader, ConfigSource}

final case class PostgresConfig(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String
) derives ConfigReader

final case class $domain_name;format="cap"$ServiceConfig(
    port: Int,
    metricsPort: Int,
    serviceName: String,
    asyncApiHtmlDir: String,
    postgres: PostgresConfig
) derives ConfigReader

object $domain_name;format="cap"$ServiceConfig {
  def load[F[_]: Sync]: F[$domain_name;format="cap"$ServiceConfig] =
    Sync[F].delay(ConfigSource.default.loadOrThrow[$domain_name;format="cap"$ServiceConfig])

  /** `load`, plus parsing/validating `port` as an `ip4s.Port` - so `Main`
    * doesn't have to do that validation itself.
    */
  def loadValidated[F[_]: Sync]: F[($domain_name;format="cap"$ServiceConfig, Port)] =
    for {
      config <- load[F]
      port <- Sync[F].fromOption(
        Port.fromInt(config.port),
        new IllegalArgumentException(
          s"Invalid $domain_name$-service port: \${config.port}"
        )
      )
    } yield (config, port)
}
