package orderservice.examples

import cats.effect.{IO, Ref, Resource}
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.{Request, Response, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.otel4s.metrics.Meter
import purerest.resilience.{
  CircuitBreakerConfig,
  CircuitBreakerOpen,
  Resilience,
  ResilienceConfig,
  RetryConfig
}

import scala.concurrent.duration._

/** This template's reference service doesn't call any other service, so
  * purerest's retry + circuit-breaker middleware isn't wired into `Main`. When
  * you adapt this template to call a real downstream service, wrap its http4s
  * `Client[F]` with `Resilience.middleware` before building your own client on
  * top of it, as shown here — this test exercises that exact pattern against a
  * dummy client, so it stays correct as purerest evolves. See README's "Calling
  * other services with resilience".
  */
class ClientResilienceExampleSuite extends CatsEffectSuite {

  private def dummyDownstreamClient(
      counter: Ref[IO, Int]
  )(behavior: Int => Status): Client[IO] =
    Client[IO] { _ =>
      Resource.eval(
        counter.updateAndGet(_ + 1).map(n => Response[IO](behavior(n)))
      )
    }

  test("a transient downstream failure is retried until it succeeds") {
    for {
      counter <- Ref.of[IO, Int](0)
      client = dummyDownstreamClient(counter)(n =>
        if (n < 3) Status.InternalServerError else Status.Ok
      )
      config = ResilienceConfig(
        retry = RetryConfig(maxRetries = 5, baseDelay = 1.millisecond),
        circuitBreaker =
          CircuitBreakerConfig(failureThreshold = 10, resetTimeout = 1.hour)
      )
      resilientClient = Resilience.middleware[IO](config)(NoOpLogger[IO])(
        Meter.noop[IO]
      )(client)
      response <- resilientClient.run(Request[IO]()).use(IO.pure)
      attempts <- counter.get
    } yield {
      assertEquals(response.status, Status.Ok)
      assertEquals(attempts, 3)
    }
  }

  test("the circuit breaker opens after repeated failures and fails fast") {
    for {
      counter <- Ref.of[IO, Int](0)
      client = dummyDownstreamClient(counter)(_ => Status.InternalServerError)
      config = ResilienceConfig(
        retry = RetryConfig(maxRetries = 0, baseDelay = 1.millisecond),
        circuitBreaker =
          CircuitBreakerConfig(failureThreshold = 2, resetTimeout = 1.hour)
      )
      resilientClient = Resilience.middleware[IO](config)(NoOpLogger[IO])(
        Meter.noop[IO]
      )(client)
      _ <- resilientClient.run(Request[IO]()).use(IO.pure).attempt
      _ <- resilientClient.run(Request[IO]()).use(IO.pure).attempt
      openResult <- resilientClient.run(Request[IO]()).use(IO.pure).attempt
      attempts <- counter.get
    } yield {
      assertEquals(openResult, Left(CircuitBreakerOpen))
      // the third call was rejected by the open breaker before ever reaching
      // the dummy client
      assertEquals(attempts, 2)
    }
  }
}
