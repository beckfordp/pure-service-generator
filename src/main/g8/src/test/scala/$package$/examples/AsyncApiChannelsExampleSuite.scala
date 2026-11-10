// SPDX-License-Identifier: MIT

package $package$.examples

import io.circe.parser.parse
import munit.FunSuite
import purekafka.docs.{
  AsyncApiChannel,
  AsyncApiDoc,
  AsyncApiSpecWriter,
  ChannelDirection
}

import java.nio.file.Files

class AsyncApiChannelsExampleSuite extends FunSuite {

  test(
    "ExampleAsyncApiChannels registers exactly the illustrative ExampleEvent channel"
  ) {
    assertEquals(
      ExampleAsyncApiChannels.channels.map(c => c.topic -> c.eventType),
      List("$domain_name$-events" -> "ExampleEvent")
    )
  }

  test("the illustrative channel is Subscribe-only") {
    assertEquals(
      ExampleAsyncApiChannels.channels.map(_.direction),
      List(ChannelDirection.Subscribe: ChannelDirection)
    )
  }

  test(
    "$package$.examples.ExampleAsyncApiChannels is reflectively loadable by name, matching build.sbt's asyncApiChannelsClass setting - a regression guard for sbt-purekafka-docs' AsyncApiSpecWriter"
  ) {
    val outputPath =
      Files.createTempFile("$domain_name$-service-asyncapi-test", ".json")
    outputPath.toFile.deleteOnExit()

    AsyncApiSpecWriter.main(
      Array(
        "$package$.examples.ExampleAsyncApiChannels",
        "$domain_name;format="cap"$ Service",
        "1.0",
        outputPath.toString
      )
    )

    val written = parse(Files.readString(outputPath)).toOption.get
    val expected = AsyncApiDoc.generate(
      "$domain_name;format="cap"$ Service",
      "1.0",
      ExampleAsyncApiChannels.channels
    )

    assertEquals(written, expected)
  }

  test(
    "the generated AsyncAPI document constrains ExampleEvent.status to the enum [created, updated, deleted]"
  ) {
    val doc = AsyncApiDoc.generate(
      "$domain_name;format="cap"$ Service",
      "1.0",
      ExampleAsyncApiChannels.channels
    )

    // Read from the real generated document, not just the Scala-side
    // Validator/Schema values - proves the enum constraint reaches it.
    val status = doc.hcursor
      .downField("channels")
      .downField("$domain_name$-events")
      .downField("messages")
      .downField("ExampleEvent")
      .downField("payload")
      .downField("properties")
      .downField("status")
    assertEquals(
      status.downField("enum").as[List[String]].toOption,
      Some(List("created", "updated", "deleted")),
      s"expected the generated doc to constrain status to the enum, got: \$doc"
    )
  }

  test(
    "the generated AsyncAPI 3.0 document receives ExampleEvent, with a description and field examples"
  ) {
    val doc = AsyncApiDoc.generate(
      "$domain_name;format="cap"$ Service",
      "1.0",
      ExampleAsyncApiChannels.channels
    )
    val root = doc.hcursor

    assertEquals(root.downField("asyncapi").as[String].toOption, Some("3.0.0"))
    assertEquals(
      root
        .downField("operations")
        .downField("$domain_name$-events")
        .downField("action")
        .as[String]
        .toOption,
      Some("receive")
    )
    val payload = root
      .downField("channels")
      .downField("$domain_name$-events")
      .downField("messages")
      .downField("ExampleEvent")
      .downField("payload")
    assert(payload.downField("description").as[String].isRight)
    assert(
      payload
        .downField("properties")
        .downField("id")
        .downField("examples")
        .succeeded,
      s"expected an example on ExampleEvent.id, got: \$doc"
    )
  }
}
