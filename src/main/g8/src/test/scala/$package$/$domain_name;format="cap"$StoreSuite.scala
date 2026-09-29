package $package$

import cats.effect.IO
import munit.CatsEffectSuite

class $domain_name;format="cap"$StoreSuite extends CatsEffectSuite {

  test("create returns an entity for the requested item and quantity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      entity <- store.create("widget", 2)
    } yield {
      assertEquals(entity.item, "widget")
      assertEquals(entity.quantity, 2)
      assertEquals(entity.status, "created")
      assert(entity.id.nonEmpty)
    }
  }

  test("get returns the persisted entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      created <- store.create("widget", 2)
      found <- store.get(created.id)
    } yield assertEquals(found, Some(created))
  }

  test("get returns None for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      found <- store.get("unknown-id")
    } yield assertEquals(found, None)
  }

  test("create produces distinct ids across calls") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      first <- store.create("widget", 1)
      second <- store.create("widget", 1)
    } yield assertNotEquals(first.id, second.id)
  }

  test("update changes quantity and status and returns the updated entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      created <- store.create("widget", 2)
      updated <- store.update(created.id, 5, "shipped")
    } yield {
      assertEquals(updated.map(_.id), Some(created.id))
      assertEquals(updated.map(_.item), Some("widget"))
      assertEquals(updated.map(_.quantity), Some(5))
      assertEquals(updated.map(_.status), Some("shipped"))
      assert(
        updated.exists(!_.updatedAt.isBefore(created.updatedAt)),
        s"expected updatedAt not to move backwards, got: \$updated"
      )
    }
  }

  test("update returns None for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      result <- store.update("unknown-id", 5, "shipped")
    } yield assertEquals(result, None)
  }

  test("delete removes the entity and returns true, and get then returns None") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
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
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      deleted <- store.delete("unknown-id")
    } yield assert(!deleted)
  }
}
