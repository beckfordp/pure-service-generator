package $package$

import cats.effect.{Async, Ref, Resource, Sync}
import cats.effect.std.Console
import cats.syntax.all._
import fs2.io.net.Network
import $package$.domain.$domain_name;format="cap"$Id
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter
import skunk.Session
import skunk.codec.all._
import skunk.implicits._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.SECONDS

final case class $domain_name;format="cap"$(
    id: $domain_name;format="cap"$Id,
    // codegen:fields:CASE_CLASS_FIELD
    createdAt: java.time.Instant,
    updatedAt: java.time.Instant
)

trait $domain_name;format="cap"$Store[F[_]] {
  def create(/* codegen:fields:CREATE_PARAMS */): F[$domain_name;format="cap"$]
  def get(id: $domain_name;format="cap"$Id): F[Option[$domain_name;format="cap"$]]
  def update(id: $domain_name;format="cap"$Id/* codegen:fields:UPDATE_PARAMS */): F[Option[$domain_name;format="cap"$]]
  def delete(id: $domain_name;format="cap"$Id): F[Boolean]
  def list(limit: Int, offset: Int): F[(List[$domain_name;format="cap"$], Long)]
  def ping: F[Boolean]
}

object $domain_name;format="cap"$Store {

  // codegen:fields:DEFAULT_VALUE_DECLS

  def inMemory[F[_]: Sync]: F[$domain_name;format="cap"$Store[F]] =
    Ref.of[F, Map[$domain_name;format="cap"$Id, $domain_name;format="cap"$]](Map.empty).map { ref =>
      new $domain_name;format="cap"$Store[F] {
        def create(/* codegen:fields:CREATE_PARAMS */): F[$domain_name;format="cap"$] =
          for {
            id <- Sync[F].delay(
              $domain_name;format="cap"$Id.fromUuid(
                java.util.UUID.randomUUID()
              )
            )
            now <- Sync[F].realTimeInstant
            entity = $domain_name;format="cap"$(id, /* codegen:fields:CONSTRUCT_ARGS */now, now)
            _ <- ref.update(_ + (id -> entity))
          } yield entity

        def get(id: $domain_name;format="cap"$Id): F[Option[$domain_name;format="cap"$]] = ref.get.map(_.get(id))

        def update(
            id: $domain_name;format="cap"$Id
            /* codegen:fields:UPDATE_PARAMS */
        ): F[Option[$domain_name;format="cap"$]] =
          for {
            now <- Sync[F].realTimeInstant
            updated <- ref.modify { entities =>
              entities.get(id) match {
                case None           => (entities, None)
                case Some(existing) =>
                  val next =
                    existing.copy(
                      /* codegen:fields:COPY_ARGS */
                      updatedAt = now
                    )
                  (entities + (id -> next), Some(next))
              }
            }
          } yield updated

        def delete(id: $domain_name;format="cap"$Id): F[Boolean] =
          ref.modify { entities =>
            if (entities.contains(id)) (entities - id, true) else (entities, false)
          }

        def list(limit: Int, offset: Int): F[(List[$domain_name;format="cap"$], Long)] =
          ref.get.map { entities =>
            val sorted = entities.values.toList
              .sortBy(_.createdAt)(using Ordering[java.time.Instant].reverse)
            (sorted.slice(offset, offset + limit), sorted.size.toLong)
          }

        def ping: F[Boolean] = Sync[F].pure(true)
      }
    }

  private val insert$domain_name;format="cap"$
      : skunk.Query[(UUID/* codegen:fields:SQL_INSERT_TUPLE_TYPE */), (OffsetDateTime, OffsetDateTime)] =
    sql"""
      INSERT INTO "$domain_name$" (id/* codegen:fields:SQL_INSERT_COLUMNS */)
      VALUES (\$uuid/* codegen:fields:SQL_INSERT_PARAMS */)
      RETURNING created_at, updated_at
    """.query(timestamptz *: timestamptz)

  private val select$domain_name;format="cap"$: skunk.Query[
    UUID,
    (/* codegen:fields:SQL_SELECT_TUPLE_TYPE */OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      SELECT /* codegen:fields:SQL_SELECT_COLUMNS */created_at, updated_at
      FROM "$domain_name$"
      WHERE id = \$uuid
    """.query(/* codegen:fields:SQL_SELECT_CODEC */timestamptz *: timestamptz)

  private val update$domain_name;format="cap"$: skunk.Query[
    (/* codegen:fields:SQL_UPDATE_TUPLE_TYPE */UUID),
    (/* codegen:fields:SQL_SELECT_TUPLE_TYPE */OffsetDateTime, OffsetDateTime)
  ] =
    sql"""
      UPDATE "$domain_name$"
      SET /* codegen:fields:SQL_UPDATE_SET */updated_at = now()
      WHERE id = \$uuid
      RETURNING /* codegen:fields:SQL_SELECT_COLUMNS */created_at, updated_at
    """.query(/* codegen:fields:SQL_SELECT_CODEC */timestamptz *: timestamptz)

  private val delete$domain_name;format="cap"$: skunk.Query[UUID, UUID] =
    sql"""
      DELETE FROM "$domain_name$"
      WHERE id = \$uuid
      RETURNING id
    """.query(uuid)

  private val pingQuery: skunk.Query[skunk.Void, Int] = sql"SELECT 1".query(
    int4
  )

  // count(*) OVER() rides along with each row so the page and its total are
  // read from one consistent query result, not two separate round trips that
  // could otherwise observe different data if a write lands in between.
  private val select$domain_name;format="cap"$Page: skunk.Query[
    (Int, Int),
    (UUID, /* codegen:fields:SQL_SELECT_TUPLE_TYPE */OffsetDateTime, OffsetDateTime, Long)
  ] =
    sql"""
      SELECT id, /* codegen:fields:SQL_SELECT_COLUMNS */created_at, updated_at,
             count(*) OVER() AS total_count
      FROM "$domain_name$"
      ORDER BY created_at DESC
      LIMIT \$int4 OFFSET \$int4
    """.query(
      uuid *: /* codegen:fields:SQL_SELECT_CODEC */timestamptz *: timestamptz *: int8
    )

  // Fallback for when select$domain_name;format="cap"$Page returns zero rows (empty
  // table, or offset past the end) -- count(*) OVER() has no row to ride along
  // with in that case, so the total still needs a direct query.
  private val count$domain_name;format="cap"$: skunk.Query[skunk.Void, Long] =
    sql"""SELECT count(*) FROM "$domain_name$"""".query(int8)

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
              * semantic-convention names), plus `error.type` if it fails - this
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
              def create(/* codegen:fields:CREATE_PARAMS */): F[$domain_name;format="cap"$] =
                for {
                  id <- Sync[F].delay(UUID.randomUUID())
                  timestamps <- timed("insert") {
                    pool.use { session =>
                      session
                        .prepare(insert$domain_name;format="cap"$)
                        .flatMap(_.unique((id/* codegen:fields:SQL_INSERT_TUPLE_ARGS */)))
                    }
                  }
                } yield {
                  val (createdAt, updatedAt) = timestamps
                  $domain_name;format="cap"$(
                    $domain_name;format="cap"$Id.fromUuid(id),
                    /* codegen:fields:CONSTRUCT_ARGS */
                    createdAt.toInstant,
                    updatedAt.toInstant
                  )
                }

              def get(id: $domain_name;format="cap"$Id): F[Option[$domain_name;format="cap"$]] =
                timed("select") {
                  pool.use { session =>
                    session
                      .prepare(select$domain_name;format="cap"$)
                      .flatMap(
                        _.option($domain_name;format="cap"$Id.uuid(id))
                      )
                  }
                }.map(_.map {
                  case (/* codegen:fields:SQL_SELECT_PATTERN_VARS */createdAt, updatedAt) =>
                    $domain_name;format="cap"$(
                      id,
                      /* codegen:fields:SQL_SELECT_CONSTRUCT_ARGS */
                      createdAt.toInstant,
                      updatedAt.toInstant
                    )
                })

              def update(
                  id: $domain_name;format="cap"$Id
                  /* codegen:fields:UPDATE_PARAMS */
              ): F[Option[$domain_name;format="cap"$]] =
                timed("update") {
                  pool.use { session =>
                    session
                      .prepare(update$domain_name;format="cap"$)
                      .flatMap(
                        _.option(
                          (
                            /* codegen:fields:SQL_UPDATE_TUPLE_ARGS */$domain_name;format="cap"$Id
                              .uuid(id)
                          )
                        )
                      )
                  }
                }.map(_.map {
                  case (/* codegen:fields:SQL_SELECT_PATTERN_VARS */createdAt, updatedAt) =>
                    $domain_name;format="cap"$(
                      id,
                      /* codegen:fields:SQL_SELECT_CONSTRUCT_ARGS */
                      createdAt.toInstant,
                      updatedAt.toInstant
                    )
                })

              def delete(id: $domain_name;format="cap"$Id): F[Boolean] =
                timed("delete") {
                  pool.use { session =>
                    session
                      .prepare(delete$domain_name;format="cap"$)
                      .flatMap(
                        _.option($domain_name;format="cap"$Id.uuid(id))
                      )
                      .map(_.isDefined)
                  }
                }

              def list(
                  limit: Int,
                  offset: Int
              ): F[(List[$domain_name;format="cap"$], Long)] =
                for {
                  rows <- timed("list") {
                    pool.use { session =>
                      session
                        .prepare(select$domain_name;format="cap"$Page)
                        .flatMap(_.stream((limit, offset), 1024).compile.toList)
                    }
                  }
                  total <- rows.headOption match {
                    case Some(row) =>
                      // The row's last element is always count(*) OVER()'s
                      // total_count; using productElement keeps this working
                      // regardless of how many field-spec columns precede it.
                      Sync[F].pure(
                        row
                          .productElement(row.productArity - 1)
                          .asInstanceOf[Long]
                      )
                    case None =>
                      timed("count") { pool.use(_.unique(count$domain_name;format="cap"$)) }
                  }
                } yield rows.map {
                  case (
                        id,
                        /* codegen:fields:SQL_SELECT_PATTERN_VARS */createdAt,
                        updatedAt,
                        _
                      ) =>
                    $domain_name;format="cap"$(
                      $domain_name;format="cap"$Id.fromUuid(id),
                      /* codegen:fields:SQL_SELECT_CONSTRUCT_ARGS */
                      createdAt.toInstant,
                      updatedAt.toInstant
                    )
                } -> total

              def ping: F[Boolean] =
                timed("ping") {
                  pool.use(_.unique(pingQuery))
                }.attempt.map(_.isRight)
            }
          }
      }
  }
}
