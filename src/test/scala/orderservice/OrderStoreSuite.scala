package orderservice

import cats.effect.IO
import munit.CatsEffectSuite

class OrderStoreSuite extends CatsEffectSuite {

  test("create returns an order for the requested item and quantity") {
    for {
      store <- OrderStore.inMemory[IO]
      order <- store.create("widget", 2)
    } yield {
      assertEquals(order.item, "widget")
      assertEquals(order.quantity, 2)
      assertEquals(order.status, "created")
      assert(order.id.nonEmpty)
    }
  }

  test("get returns the persisted order") {
    for {
      store <- OrderStore.inMemory[IO]
      created <- store.create("widget", 2)
      found <- store.get(created.id)
    } yield assertEquals(found, Some(created))
  }

  test("get returns None for an unknown id") {
    for {
      store <- OrderStore.inMemory[IO]
      found <- store.get("unknown-id")
    } yield assertEquals(found, None)
  }

  test("create produces distinct ids across calls") {
    for {
      store <- OrderStore.inMemory[IO]
      first <- store.create("widget", 1)
      second <- store.create("widget", 1)
    } yield assertNotEquals(first.id, second.id)
  }

  test("update changes quantity and status and returns the updated order") {
    for {
      store <- OrderStore.inMemory[IO]
      created <- store.create("widget", 2)
      updated <- store.update(created.id, 5, "shipped")
    } yield {
      assertEquals(updated.map(_.id), Some(created.id))
      assertEquals(updated.map(_.item), Some("widget"))
      assertEquals(updated.map(_.quantity), Some(5))
      assertEquals(updated.map(_.status), Some("shipped"))
      assert(
        updated.exists(!_.updatedAt.isBefore(created.updatedAt)),
        s"expected updatedAt not to move backwards, got: $updated"
      )
    }
  }

  test("update returns None for an unknown id") {
    for {
      store <- OrderStore.inMemory[IO]
      result <- store.update("unknown-id", 5, "shipped")
    } yield assertEquals(result, None)
  }

  test("delete removes the order and returns true, and get then returns None") {
    for {
      store <- OrderStore.inMemory[IO]
      created <- store.create("widget", 2)
      deleted <- store.delete(created.id)
      found <- store.get(created.id)
    } yield {
      assert(deleted)
      assertEquals(found, None)
    }
  }

  test("delete returns false for an unknown id") {
    for {
      store <- OrderStore.inMemory[IO]
      deleted <- store.delete("unknown-id")
    } yield assert(!deleted)
  }
}
