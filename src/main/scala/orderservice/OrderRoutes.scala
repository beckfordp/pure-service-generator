package orderservice

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

final case class CreateOrderRequest(item: String, quantity: Int)

object CreateOrderRequest {
  implicit val codec: Codec[CreateOrderRequest] = deriveCodec
}

final case class UpdateOrderRequest(quantity: Int, status: String)

object UpdateOrderRequest {
  implicit val codec: Codec[UpdateOrderRequest] = deriveCodec
}

final case class OrderResponse(
    id: String,
    item: String,
    quantity: Int,
    status: String,
    createdAt: java.time.Instant,
    updatedAt: java.time.Instant
)

object OrderResponse {
  implicit val codec: Codec[OrderResponse] = deriveCodec

  def apply(order: Order): OrderResponse =
    OrderResponse(
      order.id,
      order.item,
      order.quantity,
      order.status,
      order.createdAt,
      order.updatedAt
    )
}

final case class ErrorResponse(error: String)

object ErrorResponse {
  implicit val codec: Codec[ErrorResponse] = deriveCodec
}

object OrderRoutes {

  private val createOrderEndpoint
      : PublicEndpoint[CreateOrderRequest, Unit, OrderResponse, Any] =
    endpoint.post
      .in("orders")
      .in(jsonBody[CreateOrderRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[OrderResponse])

  private val notFoundOutput: EndpointOutput[OrderError] =
    statusCode(StatusCode.NotFound)
      .and(jsonBody[ErrorResponse])
      .map[OrderError](_ => OrderNotFound)(_ =>
        ErrorResponse("Order not found")
      )

  private val getOrderEndpoint
      : PublicEndpoint[String, OrderError, OrderResponse, Any] =
    endpoint.get
      .in("orders" / path[String]("id"))
      .out(jsonBody[OrderResponse])
      .errorOut(notFoundOutput)

  private val updateOrderEndpoint: PublicEndpoint[
    (String, UpdateOrderRequest),
    OrderError,
    OrderResponse,
    Any
  ] =
    endpoint.patch
      .in("orders" / path[String]("id"))
      .in(jsonBody[UpdateOrderRequest])
      .out(jsonBody[OrderResponse])
      .errorOut(notFoundOutput)

  private val deleteOrderEndpoint
      : PublicEndpoint[String, OrderError, Unit, Any] =
    endpoint.delete
      .in("orders" / path[String]("id"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundOutput)

  def serverEndpoint[F[_]: Async](
      store: OrderStore[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    createOrderEndpoint.serverLogicSuccess[F] { req =>
      for {
        _ <- logger.info(
          Map(
            "method" -> "POST",
            "path" -> "/orders",
            "item" -> req.item,
            "quantity" -> req.quantity.toString
          )
        )("Received request")
        order <- store.create(req.item, req.quantity).onError { case error =>
          logger.error(
            Map("item" -> req.item, "quantity" -> req.quantity.toString),
            error
          )("Persisting the order failed")
        }
        _ <- logger.info(
          Map(
            "order_id" -> order.id,
            "item" -> order.item,
            "quantity" -> order.quantity.toString
          )
        )("Request completed")
      } yield OrderResponse(order)
    }

  def getOrderServerEndpoint[F[_]: Async](
      store: OrderStore[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    getOrderEndpoint.serverLogic[F] { id =>
      for {
        _ <- logger.info(
          Map("method" -> "GET", "path" -> s"/orders/$id", "order_id" -> id)
        )(
          "Received request"
        )
        result <- store.get(id).flatMap {
          case Some(order) =>
            logger
              .info(Map("order_id" -> id))("Request completed")
              .as(Right(OrderResponse(order)))
          case None =>
            logger
              .warn(Map("order_id" -> id))("Order not found")
              .as(Left(OrderNotFound))
        }
      } yield result
    }

  def updateOrderServerEndpoint[F[_]: Async](
      store: OrderStore[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    updateOrderEndpoint.serverLogic[F] { case (id, req) =>
      for {
        _ <- logger.info(
          Map(
            "method" -> "PATCH",
            "path" -> s"/orders/$id",
            "order_id" -> id,
            "quantity" -> req.quantity.toString,
            "status" -> req.status
          )
        )("Received request")
        result <- store.update(id, req.quantity, req.status).flatMap {
          case Some(order) =>
            logger
              .info(Map("order_id" -> id))("Request completed")
              .as(Right(OrderResponse(order)))
          case None =>
            logger
              .warn(Map("order_id" -> id))("Order not found")
              .as(Left(OrderNotFound))
        }
      } yield result
    }

  def deleteOrderServerEndpoint[F[_]: Async](
      store: OrderStore[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    deleteOrderEndpoint.serverLogic[F] { id =>
      for {
        _ <- logger.info(
          Map("method" -> "DELETE", "path" -> s"/orders/$id", "order_id" -> id)
        )("Received request")
        result <- store.delete(id).flatMap {
          case true =>
            logger
              .info(Map("order_id" -> id))("Request completed")
              .as(Right(()))
          case false =>
            logger
              .warn(Map("order_id" -> id))("Order not found")
              .as(Left(OrderNotFound))
        }
      } yield result
    }

  def routes[F[_]: Async](
      store: OrderStore[F],
      logger: StructuredLogger[F]
  ): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(
      List(
        serverEndpoint(store, logger),
        getOrderServerEndpoint(store, logger),
        updateOrderServerEndpoint(store, logger),
        deleteOrderServerEndpoint(store, logger)
      )
    )
}
