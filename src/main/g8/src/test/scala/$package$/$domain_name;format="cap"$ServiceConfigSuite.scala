package $package$

import cats.effect.IO
import munit.CatsEffectSuite
import pureconfig.ConfigSource

class $domain_name;format="cap"$ServiceConfigSuite extends CatsEffectSuite {

  private val validHocon =
    """
      |port = 8080
      |metrics-port = 9090
      |service-name = "$domain_name$-service"
      |postgres {
      |  host = "localhost"
      |  port = 5432
      |  database = "$domain_name$"
      |  user = "$domain_name$"
      |  password = "$domain_name$"
      |}
      |""".stripMargin

  test("loads a fully-specified config") {
    val result =
      ConfigSource.string(validHocon).load[$domain_name;format="cap"$ServiceConfig]
    assertEquals(
      result,
      Right(
        $domain_name;format="cap"$ServiceConfig(
          port = 8080,
          metricsPort = 9090,
          serviceName = "$domain_name$-service",
          postgres = PostgresConfig(
            host = "localhost",
            port = 5432,
            database = "$domain_name$",
            user = "$domain_name$",
            password = "$domain_name$"
          )
        )
      )
    )
  }

  test("fails to load when a required field is missing") {
    val missingPassword =
      """
        |port = 8080
        |metrics-port = 9090
        |postgres {
        |  host = "localhost"
        |  port = 5432
        |  database = "$domain_name$"
        |  user = "$domain_name$"
        |}
        |""".stripMargin

    assert(
      ConfigSource
        .string(missingPassword)
        .load[$domain_name;format="cap"$ServiceConfig]
        .isLeft
    )
  }

  test("load[F] reads the shipped application.conf defaults") {
    $domain_name;format="cap"$ServiceConfig.load[IO].map { config =>
      assertEquals(config.port, 8080)
      assertEquals(config.metricsPort, 9090)
      assertEquals(config.serviceName, "$domain_name$-service")
      assertEquals(
        config.postgres,
        PostgresConfig(
          "localhost",
          5432,
          "$domain_name$",
          "$domain_name$",
          "$domain_name$"
        )
      )
    }
  }
}
