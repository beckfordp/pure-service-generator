package $package$

import cats.effect.{Async, Ref, Resource, Sync}
import cats.effect.std.Console
import cats.syntax.all._
import fs2.io.net.Network
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter
import skunk.Session
import skunk.codec.all._
import skunk.implicits._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.SECONDS

final case class $domain_name;format="cap"$(
    id: String,
    item: String,
    quantity: Int,
    status: String,
    createdAt: java.time.Instant,
    updatedAt: java.time.Instant
)

trait $domain_name;format="cap"$Store[F[_]] {
  def create(item: String, quantity: Int): F[$domain_name;format="cap"$]
  def get(id: String): F[Option[$domain_name;format="cap"$]]
  def update(id: String, quantity: Int, status: String): F[Option[$domain_name;format="cap"$]]
  def delete(id: String): F[Boolean]
  def ping: F[Boolean]
}

object $domain_name;format="cap"$Store {

  private val defaultStatus = "created"

  def inMemory[F[_]: Sync]: F[$domain_name;format="cap"$Store[F]] =
    Ref.of[F, Map[String, $domain_name;format="cap"$]](Map.empty).map { ref =>
      new $domain_name;format="cap"$Store[F] {
        def create(item: String, quantity: Int): F[$domain_name;format="cap"$] =
          for {
            id <- Sync[F].delay(java.util.UUID.randomUUID().toString)
            now <- Sync[F].realTimeInstant
            entity = $domain_name;format="cap"$(id, item, quantity, defaultStatus, now, now)
            _ <- ref.update(_ + (id -> entity))
          } yield entity

        def get(id: String): F[Option[$domain_name;format="cap"$]] = ref.get.map(_.get(id))

        def update(
            id: String,
            quantity: Int,
            status: String
        ): F[Option[$domain_name;format="cap"$]] =
          for {
            now <- Sync[F].realTimeInstant
            updated <- ref.modify { entities =>
              entities.get(id) match {
                case None           => (entities, None)
                case Some(existing) =>
                  val next =
                    existing.copy(
                      quantity = quantity,
                      status = status,
                      updatedAt = now
                    )
                  (entities + (id -> next), Some(next))
              }
            }
          } yield updated

        def delete(id: String): F[Boolean] =
          ref.modify { entities =>
            if (entities.contains(id)) (entities - id, true) else (entities, false)
          }

        def ping: F[Boolean] = Sync[F].pure(true)
      }
    }

  private val insert$domain_name;format="cap"$
      : skunk.Query[(UUID, String, Int), (OffsetDateTime, OffsetDateTime)] =
    sql"""
      INSERT INTO "$domain_name$" (id, item, quantity)
      VALUES (\$uuid, \$text, \$int4)
      RETURNING created_at, updated_at
    """.query(timestamptz *: timestamptz)

  private val select$domain_name;format="cap"$: skunk.Query[
    UUID,
    (String, Int, String, OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      SELECT item, quantity, status, created_at, updated_at
      FROM "$domain_name$"
      WHERE id = \$uuid
    """.query(text *: int4 *: text *: timestamptz *: timestamptz)

  private val update$domain_name;format="cap"$: skunk.Query[
    (Int, String, UUID),
    (String, Int, String, OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      UPDATE "$domain_name$"
      SET quantity = \$int4, status = \$text, updated_at = now()
      WHERE id = \$uuid
      RETURNING item, quantity, status, created_at, updated_at
    """.query(text *: int4 *: text *: timestamptz *: timestamptz)

  private val delete$domain_name;format="cap"$: skunk.Query[UUID, UUID] =
    sql"""
      DELETE FROM "$domain_name$"
      WHERE id = \$uuid
      RETURNING id
    """.query(uuid)

  private val pingQuery: skunk.Query[skunk.Void, Int] = sql"SELECT 1".query(
    int4
  )

  def postgres[F[_]: Async: Console: Network](
      config: PostgresConfig,
      meter: Meter[F]
  ): Resource[F, $domain_name;format="cap"$Store[F]] = {
    import org.typelevel.otel4s.trace.Tracer.Implicits.noop
    import org.typelevel.otel4s.metrics.Meter.Implicits.noop
    Session
      .Builder[F]
      .withHost(config.host)
      .withPort(config.port)
      .withUserAndPassword(config.user, config.password)
      .withDatabase(config.database)
      .pooled(max = 10)
      .evalMap { pool =>
        meter
          .histogram[Double]("db.client.operation.duration")
          .withUnit("s")
          .create
          .map { histogram =>
            /** Times a Skunk query, recording a `db.client.operation.duration`
              * measurement tagged with `db.system`/`db.operation` (OTel
              * semantic-convention names), plus `error.type` if it fails — this
              * is this service's only Postgres consumer, so it's instrumented
              * directly here rather than via a new purerest combinator.
              */
            def timed[A](operation: String)(fa: F[A]): F[A] =
              for {
                start <- Async[F].monotonic
                result <- fa.attempt
                end <- Async[F].monotonic
                outcomeAttributes = result match {
                  case Right(_)    => Nil
                  case Left(error) =>
                    List(Attribute("error.type", error.getClass.getName))
                }
                _ <- histogram.record(
                  (end - start).toUnit(SECONDS),
                  List(
                    Attribute("db.system", "postgresql"),
                    Attribute("db.operation", operation)
                  ) ++ outcomeAttributes
                )
                a <- result.liftTo[F]
              } yield a

            new $domain_name;format="cap"$Store[F] {
              def create(item: String, quantity: Int): F[$domain_name;format="cap"$] =
                for {
                  id <- Sync[F].delay(UUID.randomUUID())
                  timestamps <- timed("insert") {
                    pool.use { session =>
                      session
                        .prepare(insert$domain_name;format="cap"$)
                        .flatMap(_.unique((id, item, quantity)))
                    }
                  }
                } yield {
                  val (createdAt, updatedAt) = timestamps
                  $domain_name;format="cap"$(
                    id.toString,
                    item,
                    quantity,
                    defaultStatus,
                    createdAt.toInstant,
                    updatedAt.toInstant
                  )
                }

              def get(id: String): F[Option[$domain_name;format="cap"$]] =
                scala.util.Try(UUID.fromString(id)).toOption match {
                  case None       => Sync[F].pure(None)
                  case Some(uuid) =>
                    for {
                      row <- timed("select") {
                        pool.use { session =>
                          session.prepare(select$domain_name;format="cap"$).flatMap(_.option(uuid))
                        }
                      }
                    } yield row.map {
                      case (item, quantity, status, createdAt, updatedAt) =>
                        $domain_name;format="cap"$(
                          id,
                          item,
                          quantity,
                          status,
                          createdAt.toInstant,
                          updatedAt.toInstant
                        )
                    }
                }

              def update(
                  id: String,
                  quantity: Int,
                  status: String
              ): F[Option[$domain_name;format="cap"$]] =
                scala.util.Try(UUID.fromString(id)).toOption match {
                  case None       => Sync[F].pure(None)
                  case Some(uuid) =>
                    for {
                      row <- timed("update") {
                        pool.use { session =>
                          session
                            .prepare(update$domain_name;format="cap"$)
                            .flatMap(_.option((quantity, status, uuid)))
                        }
                      }
                    } yield row.map {
                      case (item, quantity, status, createdAt, updatedAt) =>
                        $domain_name;format="cap"$(
                          id,
                          item,
                          quantity,
                          status,
                          createdAt.toInstant,
                          updatedAt.toInstant
                        )
                    }
                }

              def delete(id: String): F[Boolean] =
                scala.util.Try(UUID.fromString(id)).toOption match {
                  case None       => Sync[F].pure(false)
                  case Some(uuid) =>
                    timed("delete") {
                      pool.use { session =>
                        session
                          .prepare(delete$domain_name;format="cap"$)
                          .flatMap(_.option(uuid))
                          .map(_.isDefined)
                      }
                    }
                }

              def ping: F[Boolean] =
                timed("ping") {
                  pool.use(_.unique(pingQuery))
                }.attempt.map(_.isRight)
            }
          }
      }
  }
}
