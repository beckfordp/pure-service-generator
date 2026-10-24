package $package$

import io.circe.parser.parse
import munit.FunSuite
import purekafka.docs.{
  AsyncApiChannel,
  AsyncApiDoc,
  AsyncApiSpecWriter,
  ChannelDirection
}

import java.nio.file.Files

class ExampleAsyncApiChannelsSuite extends FunSuite {

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
    "$package$.ExampleAsyncApiChannels is reflectively loadable by name, matching build.sbt's asyncApiChannelsClass setting - a regression guard for sbt-purekafka-docs' AsyncApiSpecWriter"
  ) {
    val outputPath =
      Files.createTempFile("$domain_name$-service-asyncapi-test", ".json")
    outputPath.toFile.deleteOnExit()

    AsyncApiSpecWriter.main(
      Array(
        "$package$.ExampleAsyncApiChannels",
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
}
