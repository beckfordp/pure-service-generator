package $package$

import cats.effect.IO
import munit.CatsEffectSuite
import $package$.domain.$domain_name;format="cap"$Id

import java.util.UUID

import scala.concurrent.duration._

class $domain_name;format="cap"$StoreSuite extends CatsEffectSuite {

  test("create returns a persisted entity with a generated id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      entity <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
    } yield assert($domain_name;format="cap"$Id.render(entity.id).nonEmpty)
  }

  test("get returns the persisted entity") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      created <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      found <- store.get(created.id)
    } yield assertEquals(found, Some(created))
  }

  test("get returns None for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      found <- store.get($domain_name;format="cap"$Id.fromUuid(UUID.randomUUID()))
    } yield assertEquals(found, None)
  }

  test("create produces distinct ids across calls") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      first <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      second <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
    } yield assertNotEquals(first.id, second.id)
  }

  test("update returns the updated entity with updatedAt not moving backwards") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      created <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      updated <- store.update(created.id/* codegen:fields:TEST_UPDATE_ARGS */)
    } yield {
      assertEquals(updated.map(_.id), Some(created.id))
      assert(
        updated.exists(!_.updatedAt.isBefore(created.updatedAt)),
        s"expected updatedAt not to move backwards, got: \$updated"
      )
    }
  }

  test("update returns None for an unknown id") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      result <- store.update($domain_name;format="cap"$Id.fromUuid(UUID.randomUUID())/* codegen:fields:TEST_UPDATE_ARGS */)
    } yield assertEquals(result, None)
  }

  test("delete removes the entity and returns true, and get then returns None") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      created <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
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
      deleted <- store.delete($domain_name;format="cap"$Id.fromUuid(UUID.randomUUID()))
    } yield assert(!deleted)
  }

  test("list returns newest-first pages with the total count") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      first <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      _ <- IO.sleep(2.millis)
      second <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      _ <- IO.sleep(2.millis)
      _ <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      (page, total) <- store.list(limit = 2, offset = 1)
    } yield {
      assertEquals(page.map(_.id), List(second.id, first.id))
      assertEquals(total, 3L)
    }
  }

  test("list on an empty store returns no entities and a zero total") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      (page, total) <- store.list(limit = 20, offset = 0)
    } yield {
      assertEquals(page, Nil)
      assertEquals(total, 0L)
    }
  }

  test("list past the end returns an empty page with the full total") {
    for {
      store <- $domain_name;format="cap"$Store.inMemory[IO]
      _ <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      _ <- store.create(/* codegen:fields:TEST_CREATE_ARGS */)
      (page, total) <- store.list(limit = 10, offset = 5)
    } yield {
      assertEquals(page, Nil)
      assertEquals(total, 2L)
    }
  }
}
