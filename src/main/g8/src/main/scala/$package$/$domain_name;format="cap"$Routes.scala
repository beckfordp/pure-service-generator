package $package$

import cats.effect.Async
import cats.syntax.all._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.typelevel.log4cats.StructuredLogger
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

final case class Create$domain_name;format="cap"$Request(/* codegen:fields:CREATE_PARAMS */)

object Create$domain_name;format="cap"$Request {
  implicit val codec: Codec[Create$domain_name;format="cap"$Request] = deriveCodec
}

final case class Update$domain_name;format="cap"$Request(/* codegen:fields:UPDATE_PARAMS */)

object Update$domain_name;format="cap"$Request {
  implicit val codec: Codec[Update$domain_name;format="cap"$Request] = deriveCodec
}

final case class $domain_name;format="cap"$Response(
    id: String,
    // codegen:fields:CASE_CLASS_FIELD
    createdAt: java.time.Instant,
    updatedAt: java.time.Instant
)

object $domain_name;format="cap"$Response {
  implicit val codec: Codec[$domain_name;format="cap"$Response] = deriveCodec

  def apply(entity: $domain_name;format="cap"$): $domain_name;format="cap"$Response =
    $domain_name;format="cap"$Response(
      entity.id,
      /* codegen:fields:RESPONSE_APPLY_ARGS */
      entity.createdAt,
      entity.updatedAt
    )
}

final case class ErrorResponse(error: String)

object ErrorResponse {
  implicit val codec: Codec[ErrorResponse] = deriveCodec
}

object $domain_name;format="cap"$Routes {

  private val create$domain_name;format="cap"$Endpoint: PublicEndpoint[
    Create$domain_name;format="cap"$Request,
    Unit,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.post
      .in("$domain_name$s")
      .in(jsonBody[Create$domain_name;format="cap"$Request])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[$domain_name;format="cap"$Response])

  private val notFoundOutput: EndpointOutput[$domain_name;format="cap"$Error] =
    statusCode(StatusCode.NotFound)
      .and(jsonBody[ErrorResponse])
      .map[$domain_name;format="cap"$Error](_ => $domain_name;format="cap"$NotFound)(_ =>
        ErrorResponse("$domain_name;format="cap"$ not found")
      )

  private val get$domain_name;format="cap"$Endpoint: PublicEndpoint[
    String,
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.get
      .in("$domain_name$s" / path[String]("id"))
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)

  private val update$domain_name;format="cap"$Endpoint: PublicEndpoint[
    (String, Update$domain_name;format="cap"$Request),
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.patch
      .in("$domain_name$s" / path[String]("id"))
      .in(jsonBody[Update$domain_name;format="cap"$Request])
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)

  private val replace$domain_name;format="cap"$Endpoint: PublicEndpoint[
    (String, Update$domain_name;format="cap"$Request),
    $domain_name;format="cap"$Error,
    $domain_name;format="cap"$Response,
    Any
  ] =
    endpoint.put
      .in("$domain_name$s" / path[String]("id"))
      .in(jsonBody[Update$domain_name;format="cap"$Request])
      .out(jsonBody[$domain_name;format="cap"$Response])
      .errorOut(notFoundOutput)

  private val delete$domain_name;format="cap"$Endpoint
      : PublicEndpoint[String, $domain_name;format="cap"$Error, Unit, Any] =
    endpoint.delete
      .in("$domain_name$s" / path[String]("id"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundOutput)

  def serverEndpoint[F[_]: Async](
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
          Map("$domain_name$_id" -> entity.id)
        )("Request completed")
      } yield $domain_name;format="cap"$Response(entity)
    }

  def get$domain_name;format="cap"$ServerEndpoint[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    get$domain_name;format="cap"$Endpoint.serverLogic[F] { id =>
      for {
        _ <- logger.info(
          Map("method" -> "GET", "path" -> s"/$domain_name$s/\$id", "$domain_name$_id" -> id)
        )(
          "Received request"
        )
        result <- store.get(id).flatMap {
          case Some(entity) =>
            logger
              .info(Map("$domain_name$_id" -> id))("Request completed")
              .as(Right($domain_name;format="cap"$Response(entity)))
          case None =>
            logger
              .warn(Map("$domain_name$_id" -> id))("$domain_name;format="cap"$ not found")
              .as(Left($domain_name;format="cap"$NotFound))
        }
      } yield result
    }

  /** Shared handler for `PATCH` (partial update) and `PUT` (full replace) —
    * both call `$domain_name;format="cap"$Store.update` with the same required
    * update body; only the logged HTTP method differs.
    */
  private def updateLogic[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F],
      httpMethod: String
  )(
      id: String,
      req: Update$domain_name;format="cap"$Request
  ): F[Either[$domain_name;format="cap"$Error, $domain_name;format="cap"$Response]] =
    for {
      _ <- logger.info(
        Map(
          "method" -> httpMethod,
          "path" -> s"/$domain_name$s/\$id",
          "$domain_name$_id" -> id
        )
      )("Received request")
      result <- store.update(id/* codegen:fields:UPDATE_CALL_ARGS */).flatMap {
        case Some(entity) =>
          logger
            .info(Map("$domain_name$_id" -> id))("Request completed")
            .as(Right($domain_name;format="cap"$Response(entity)))
        case None =>
          logger
            .warn(Map("$domain_name$_id" -> id))("$domain_name;format="cap"$ not found")
            .as(Left($domain_name;format="cap"$NotFound))
      }
    } yield result

  def update$domain_name;format="cap"$ServerEndpoint[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    update$domain_name;format="cap"$Endpoint.serverLogic[F] { case (id, req) =>
      updateLogic(store, logger, "PATCH")(id, req)
    }

  def replace$domain_name;format="cap"$ServerEndpoint[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    replace$domain_name;format="cap"$Endpoint.serverLogic[F] { case (id, req) =>
      updateLogic(store, logger, "PUT")(id, req)
    }

  def delete$domain_name;format="cap"$ServerEndpoint[F[_]: Async](
      store: $domain_name;format="cap"$Store[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    delete$domain_name;format="cap"$Endpoint.serverLogic[F] { id =>
      for {
        _ <- logger.info(
          Map("method" -> "DELETE", "path" -> s"/$domain_name$s/\$id", "$domain_name$_id" -> id)
        )("Received request")
        result <- store.delete(id).flatMap {
          case true =>
            logger
              .info(Map("$domain_name$_id" -> id))("Request completed")
              .as(Right(()))
          case false =>
            logger
              .warn(Map("$domain_name$_id" -> id))("$domain_name;format="cap"$ not found")
              .as(Left($domain_name;format="cap"$NotFound))
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
        delete$domain_name;format="cap"$ServerEndpoint(store, logger)
      )
    )
}
