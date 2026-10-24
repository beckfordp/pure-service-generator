addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
// Packages $domain_name$-service as a Docker image (JavaAppPackaging + DockerPlugin).
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
// purekafka's AsyncAPI-docs sbt plugin: generates
// target/asyncapi-docs-spec/asyncapi.json + target/asyncapi-docs/index.html
// from AsyncApiChannels automatically on every `sbt compile`. Resolves from
// ~/.ivy2/local (same as purekafka's own build.sbt entry) - no release tag
// yet, a fixed dev version from the sibling purerest checkout's own `sbt
// sbtPureKafkaDocs/publishLocal`; re-pin once a real tag exists.
addSbtPlugin("io.github.beckfordp" % "sbt-purekafka-docs" % "0.1.0-dev")
