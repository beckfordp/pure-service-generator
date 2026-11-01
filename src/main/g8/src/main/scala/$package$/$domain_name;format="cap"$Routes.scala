package $package$

import cats.{Monad, MonadThrow}
import cats.effect.Async
import cats.syntax.all._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import $package$.domain.$domain_name;format="cap"$Id
import org.http4s.HttpRoutes
import org.typelevel.log4cats.StructuredLogger
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.Schema.annotations.{description, encodedExample}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

@description("Fields for a new $domain_name$.")
final case class Create$domain_name;format="cap"$Request(/* codegen:fields:DTO_CREATE_FIELD */)

object Create$domain_name;format="cap"$Request {
  implicit val codec: Codec[Create$domain_name;format="cap"$Request] = deriveCodec
}

@description("A $domain_name$'s updatable fields; every one is required.")
final case class Update$domain_name;format="cap"$Request(/* codegen:fields:DTO_UPDATE_FIELD */)

object Update$domain_name;format="cap"$Request {
  implicit val codec: Codec[Update$domain_name;format="cap"$Request] = deriveCodec
}

@description("A $domain_name$ as stored.")
final case class $domain_name;format="cap"$Response(
    @description("$domain_name;format="cap"$ id (UUID).")
    @encodedExample("3f2b8c1e-7a4d-4e9b-9c55-0d6e2a1b7f40")
    id: String,
    // codegen:fields:DTO_RESPONSE_FIELD
    @description("When the $domain_name$ was created (UTC).")
    @encodedExample("2026-11-01T09:30:00Z")
    createdAt: java.time.Instant,
    @description("When the $domain_name$ was last changed (UTC).")
    @encodedExample("2026-11-01T09:45:00Z")
    updatedAt: java.time.Instant
)

object $domain_name;format="cap"$Response {
  implicit val codec: Codec[$domain_name;format="cap"$Response] = deriveCodec

  def apply(entity: $domain_name;format="cap"$): $domain_name;format="cap"$Response =
    $domain_name;format="cap"$Response(
      $domain_name;format="cap"$Id.render(entity.id),
      /* codegen:fields:RESPONSE_APPLY_ARGS */
      entity.createdAt,
      entity.updatedAt
    )
}

@description("Error body.")
final case class ErrorResponse(
    @description("What went wrong.") @encodedExample("$domain_name;format="cap"$ not found")
    error: String
)

object ErrorResponse {
  implicit val codec: Codec[ErrorResponse] = deriveCodec
}

object $domain_name;format="cap"$Routes {

  // REST docs convention (purerest README "Documenting endpoints"): every
  // endpoint, parameter, DTO field and error is described, and
  // $domain_name;format="cap"$DocsSuite asserts Docs.undocumented finds no gaps.
  private val tag = "$domain_name$s"

  // Each error's body, shared by its documented example and its real mapping.
  private val notFound = ErrorResponse("$domain_name;format="cap"$ not found")
  private val invalidPagination = ErrorResponse("Invalid pagination parameters")

  private val idPath: EndpointInput.PathCapture[$domain_name;format="cap"$Id] =
    path[$domain_name;format="cap"$Id]("id")
      .description("$domain_name;format="cap"$ id (UUID). A malformed id returns 400.")
      .example(
        $domain_name;format="cap"$Id.fromUuid(
          java.util.UUID.fromString("3f2b8c1e-7a4d-4e9b-9c55-0d6e2a1b7f40")
        )
      )

  private val create$domain_name;format="cap"$Endpoint: PublicEndpoint[
    Create$domain_name;format="cap"$Request,
    Unit,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.post
      .in("$domain_name$s")
      // The body example is built from the same per-field examples codegen
      // splices into the tests, so it stays complete as fields are added.
      .in(
        jsonBody[Create$domain_name;format="cap"$Request].example(
          Create$domain_name;format="cap"$Request(/* codegen:fields:TEST_CREATE_REQUEST_ARGS */)
        )
      )
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[$domain_name;format="cap"$Response])
      .summary("Create a $domain_name$")
      .description(
        "Creates a $domain_name$ from the body and returns it with its server-assigned id and timestamps. Not idempotent: each call creates a new $domain_name$."
      )
      .tag(tag)

  private val notFoundOutput: EndpointOutput[$domain_name;format="cap"$Error] =
    statusCode(StatusCode.NotFound)
      .and(
        jsonBody[ErrorResponse]
          .description("No $domain_name$ with this id.")
          .example(notFound)
      )
      .map[$domain_name;format="cap"$Error](_ => $domain_name;format="cap"$NotFound)(_ => notFound)

  private val invalidPaginationOutput: EndpointOutput[$domain_name;format="cap"$Error] =
    statusCode(StatusCode.BadRequest)
      .and(
        jsonBody[ErrorResponse]
          .description("`limit` outside 1–100 or `offset` negative.")
          .example(invalidPagination)
      )
      .map[$domain_name;format="cap"$Error](_ => InvalidPagination)(_ => invalidPagination)

  private val list$domain_name;format="cap"$sEndpoint: PublicEndpoint[
    (Option[Int], Option[Int]),
    $domain_name;format="cap"$Error,
    (List[$domain_name;format="cap"$Response], String),
    Any
  ] =
    endpoint.get
      .in("$domain_name$s")
      .in(
        query[Option[Int]]("limit")
          .description("Page size, 1–100. Defaults to 20.")
          .example(Some(20))
      )
      .in(
        query[Option[Int]]("offset")
          .description(
            "$domain_name;format="cap"$s to skip before the page starts, ≥ 0. Defaults to 0."
          )
          .example(Some(0))
      )
      .out(jsonBody[List[$domain_name;format="cap"$Response]])
      .out(
        header[String]("X-Total-Count")
          .description("Total $domain_name$s across all pages.")
      )
      .errorOut(invalidPaginationOutput)
      .summary("List $domain_name$s")
      .description(
        "Returns a page of $domain_name$s, newest first, with the total in `X-Total-Count`. Read-only. 400 if `limit`/`offset` is out of range."
      )
      .tag(tag)

  private val get$domain_name;format="cap"$Endpoint: PublicEndpoint[
    $domain_name;format="cap"$Id,
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.get
      .in("$domain_name$s" / idPath)
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)
      .summary("Get a $domain_name$")
      .description(
        "Returns the $domain_name$ with this id. Read-only. 404 if none."
      )
      .tag(tag)

  private val update$domain_name;format="cap"$Endpoint: PublicEndpoint[
    ($domain_name;format="cap"$Id, Update$domain_name;format="cap"$Request),
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.patch
      .in("$domain_name$s" / idPath)
      .in(
        jsonBody[Update$domain_name;format="cap"$Request].example(
          Update$domain_name;format="cap"$Request(/* codegen:fields:TEST_UPDATE_REQUEST_ARGS */)
        )
      )
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)
      .summary("Update a $domain_name$")
      .description(
        "Sets the $domain_name$'s updatable fields from the body and returns it. Every updatable field is required (same as PUT). Idempotent. 404 if none."
      )
      .tag(tag)

  private val replace$domain_name;format="cap"$Endpoint: PublicEndpoint[
    ($domain_name;format="cap"$Id, Update$domain_name;format="cap"$Request),
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.put
      .in("$domain_name$s" / idPath)
      .in(
        jsonBody[Update$domain_name;format="cap"$Request].example(
          Update$domain_name;format="cap"$Request(/* codegen:fields:TEST_UPDATE_REQUEST_ARGS */)
        )
      )
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)
      .summary("Replace a $domain_name$")
      .description(
        "Replaces the $domain_name$'s updatable fields with the body and returns it. Idempotent. 404 if none."
      )
      .tag(tag)

  private val delete$domain_name;format="cap"$Endpoint
      : PublicEndpoint[$domain_name;format="cap"$Id, $domain_name;format="cap"$Error, Unit, Any] =
    endpoint.delete
      .in("$domain_name$s" / idPath)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundOutput)
      .summary("Delete a $domain_name$")
      .description(
        "Deletes the $domain_name$ with this id. 404 if none, so a repeat delete returns 404."
      )
      .tag(tag)

  def serverEndpoint[F[_]: MonadThrow](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    create$domain_name;format="cap"$Endpoint.serverLogicSuccess[F] { req =>
      for {
        _ <- logger.info(
          Map(
            "method" -> "POST",
            "path" -> "/$domain_name$s"
          )
        )("Received request")
        entity <- store.create(/* codegen:fields:CREATE_CALL_ARGS */).onError { case error =>
          logger.error(Map.empty, error)("Persisting the $domain_name$ failed")
        }
        _ <- logger.info(
          Map("$domain_name$_id" -> $domain_name;format="cap"$Id.render(entity.id))
        )("Request completed")
      } yield $domain_name;format="cap"$Response(entity)
    }

  def get$domain_name;format="cap"$ServerEndpoint[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    get$domain_name;format="cap"$Endpoint.serverLogic[F] { id =>
      val renderedId = $domain_name;format="cap"$Id.render(id)
      for {
        _ <- logger.info(
          Map(
            "method" -> "GET",
            "path" -> s"/$domain_name$s/\$renderedId",
            "$domain_name$_id" -> renderedId
          )
        )("Received request")
        result <- store.get(id).flatMap {
          case Some(entity) =>
            logger
              .info(Map("$domain_name$_id" -> renderedId))("Request completed")
              .as(Right($domain_name;format="cap"$Response(entity)))
          case None =>
            logger
              .warn(Map("$domain_name$_id" -> renderedId))("$domain_name;format="cap"$ not found")
              .as(Left($domain_name;format="cap"$NotFound))
        }
      } yield result
    }

  /** Shared handler for `PATCH` (partial update) and `PUT` (full replace) —
    * both call `$domain_name;format="cap"$Store.update` with the same required
    * update body; only the logged HTTP method differs.
    */
  private def updateLogic[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F],
      httpMethod: String
  )(
      id: $domain_name;format="cap"$Id,
      req: Update$domain_name;format="cap"$Request
  ): F[Either[$domain_name;format="cap"$Error, $domain_name;format="cap"$Response]] = {
    val renderedId = $domain_name;format="cap"$Id.render(id)
    for {
      _ <- logger.info(
        Map(
          "method" -> httpMethod,
          "path" -> s"/$domain_name$s/\$renderedId",
          "$domain_name$_id" -> renderedId
        )
      )("Received request")
      result <- store.update(id/* codegen:fields:UPDATE_CALL_ARGS */).flatMap {
        case Some(entity) =>
          logger
            .info(Map("$domain_name$_id" -> renderedId))("Request completed")
            .as(Right($domain_name;format="cap"$Response(entity)))
        case None =>
          logger
            .warn(Map("$domain_name$_id" -> renderedId))("$domain_name;format="cap"$ not found")
            .as(Left($domain_name;format="cap"$NotFound))
      }
    } yield result
  }

  def update$domain_name;format="cap"$ServerEndpoint[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    update$domain_name;format="cap"$Endpoint.serverLogic[F] { case (id, req) =>
      updateLogic(store, logger, "PATCH")(id, req)
    }

  def replace$domain_name;format="cap"$ServerEndpoint[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    replace$domain_name;format="cap"$Endpoint.serverLogic[F] { case (id, req) =>
      updateLogic(store, logger, "PUT")(id, req)
    }

  def delete$domain_name;format="cap"$ServerEndpoint[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    delete$domain_name;format="cap"$Endpoint.serverLogic[F] { id =>
      val renderedId = $domain_name;format="cap"$Id.render(id)
      for {
        _ <- logger.info(
          Map(
            "method" -> "DELETE",
            "path" -> s"/$domain_name$s/\$renderedId",
            "$domain_name$_id" -> renderedId
          )
        )("Received request")
        result <- store.delete(id).flatMap {
          case true =>
            logger
              .info(Map("$domain_name$_id" -> renderedId))("Request completed")
              .as(Right(()))
          case false =>
            logger
              .warn(Map("$domain_name$_id" -> renderedId))("$domain_name;format="cap"$ not found")
              .as(Left($domain_name;format="cap"$NotFound))
        }
      } yield result
    }

  private val defaultLimit = 20
  private val maxLimit = 100

  def list$domain_name;format="cap"$sServerEndpoint[F[_]: Monad](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    list$domain_name;format="cap"$sEndpoint.serverLogic[F] { case (limitParam, offsetParam) =>
      val limit = limitParam.getOrElse(defaultLimit)
      val offset = offsetParam.getOrElse(0)
      for {
        _ <- logger.info(
          Map("method" -> "GET", "path" -> "/$domain_name$s")
        )("Received request")
        result <-
          if (limit <= 0 || limit > maxLimit || offset < 0) {
            logger
              .warn(
                Map("limit" -> limit.toString, "offset" -> offset.toString)
              )("Invalid pagination parameters")
              .as(Left(InvalidPagination))
          } else {
            store.list(limit, offset).flatMap { case (entities, total) =>
              logger
                .info(
                  Map(
                    "limit" -> limit.toString,
                    "offset" -> offset.toString,
                    "count" -> entities.size.toString,
                    "total" -> total.toString
                  )
                )("Request completed")
                .as(
                  Right(
                    (
                      entities.map($domain_name;format="cap"$Response.apply),
                      total.toString
                    )
                  )
                )
            }
          }
      } yield result
    }

  def routes[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(
      List(
        serverEndpoint(store, logger),
        get$domain_name;format="cap"$ServerEndpoint(store, logger),
        update$domain_name;format="cap"$ServerEndpoint(store, logger),
        replace$domain_name;format="cap"$ServerEndpoint(store, logger),
        delete$domain_name;format="cap"$ServerEndpoint(store, logger),
        list$domain_name;format="cap"$sServerEndpoint(store, logger)
      )
    )
}
