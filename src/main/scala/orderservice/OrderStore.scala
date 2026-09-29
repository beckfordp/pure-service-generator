package orderservice

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

final case class Order(
    id: String,
    item: String,
    quantity: Int,
    status: String,
    createdAt: java.time.Instant,
    updatedAt: java.time.Instant
)

trait OrderStore[F[_]] {
  def create(item: String, quantity: Int): F[Order]
  def get(id: String): F[Option[Order]]
  def update(id: String, quantity: Int, status: String): F[Option[Order]]
  def delete(id: String): F[Boolean]
}

object OrderStore {

  private val defaultStatus = "created"

  def inMemory[F[_]: Sync]: F[OrderStore[F]] =
    Ref.of[F, Map[String, Order]](Map.empty).map { ref =>
      new OrderStore[F] {
        def create(item: String, quantity: Int): F[Order] =
          for {
            id <- Sync[F].delay(java.util.UUID.randomUUID().toString)
            now <- Sync[F].realTimeInstant
            order = Order(id, item, quantity, defaultStatus, now, now)
            _ <- ref.update(_ + (id -> order))
          } yield order

        def get(id: String): F[Option[Order]] = ref.get.map(_.get(id))

        def update(
            id: String,
            quantity: Int,
            status: String
        ): F[Option[Order]] =
          for {
            now <- Sync[F].realTimeInstant
            updated <- ref.modify { orders =>
              orders.get(id) match {
                case None           => (orders, None)
                case Some(existing) =>
                  val next =
                    existing.copy(
                      quantity = quantity,
                      status = status,
                      updatedAt = now
                    )
                  (orders + (id -> next), Some(next))
              }
            }
          } yield updated

        def delete(id: String): F[Boolean] =
          ref.modify { orders =>
            if (orders.contains(id)) (orders - id, true) else (orders, false)
          }
      }
    }

  private val insertOrder
      : skunk.Query[(UUID, String, Int), (OffsetDateTime, OffsetDateTime)] =
    sql"""
      INSERT INTO "order" (id, item, quantity)
      VALUES ($uuid, $text, $int4)
      RETURNING created_at, updated_at
    """.query(timestamptz *: timestamptz)

  private val selectOrder: skunk.Query[
    UUID,
    (String, Int, String, OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      SELECT item, quantity, status, created_at, updated_at
      FROM "order"
      WHERE id = $uuid
    """.query(text *: int4 *: text *: timestamptz *: timestamptz)

  private val updateOrder: skunk.Query[
    (Int, String, UUID),
    (String, Int, String, OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      UPDATE "order"
      SET quantity = $int4, status = $text, updated_at = now()
      WHERE id = $uuid
      RETURNING item, quantity, status, created_at, updated_at
    """.query(text *: int4 *: text *: timestamptz *: timestamptz)

  private val deleteOrder: skunk.Query[UUID, UUID] =
    sql"""
      DELETE FROM "order"
      WHERE id = $uuid
      RETURNING id
    """.query(uuid)

  def postgres[F[_]: Async: Console: Network](
      config: PostgresConfig,
      meter: Meter[F]
  ): Resource[F, OrderStore[F]] = {
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

            new OrderStore[F] {
              def create(item: String, quantity: Int): F[Order] =
                for {
                  id <- Sync[F].delay(UUID.randomUUID())
                  timestamps <- timed("insert") {
                    pool.use { session =>
                      session
                        .prepare(insertOrder)
                        .flatMap(_.unique((id, item, quantity)))
                    }
                  }
                } yield {
                  val (createdAt, updatedAt) = timestamps
                  Order(
                    id.toString,
                    item,
                    quantity,
                    defaultStatus,
                    createdAt.toInstant,
                    updatedAt.toInstant
                  )
                }

              def get(id: String): F[Option[Order]] =
                scala.util.Try(UUID.fromString(id)).toOption match {
                  case None       => Sync[F].pure(None)
                  case Some(uuid) =>
                    for {
                      row <- timed("select") {
                        pool.use { session =>
                          session.prepare(selectOrder).flatMap(_.option(uuid))
                        }
                      }
                    } yield row.map {
                      case (item, quantity, status, createdAt, updatedAt) =>
                        Order(
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
              ): F[Option[Order]] =
                scala.util.Try(UUID.fromString(id)).toOption match {
                  case None       => Sync[F].pure(None)
                  case Some(uuid) =>
                    for {
                      row <- timed("update") {
                        pool.use { session =>
                          session
                            .prepare(updateOrder)
                            .flatMap(_.option((quantity, status, uuid)))
                        }
                      }
                    } yield row.map {
                      case (item, quantity, status, createdAt, updatedAt) =>
                        Order(
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
                          .prepare(deleteOrder)
                          .flatMap(_.option(uuid))
                          .map(_.isDefined)
                      }
                    }
                }
            }
          }
      }
  }
}
