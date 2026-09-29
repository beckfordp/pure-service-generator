package $package$

import cats.effect.Sync
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
    postgres: PostgresConfig
) derives ConfigReader

object $domain_name;format="cap"$ServiceConfig {
  def load[F[_]: Sync]: F[$domain_name;format="cap"$ServiceConfig] =
    Sync[F].delay(ConfigSource.default.loadOrThrow[$domain_name;format="cap"$ServiceConfig])
}
