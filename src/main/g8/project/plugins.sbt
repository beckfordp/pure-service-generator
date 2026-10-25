addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
// Packages $domain_name$-service as a Docker image (JavaAppPackaging + DockerPlugin).
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
// purekafka's AsyncAPI-docs sbt plugin: generates
// target/asyncapi-docs-spec/asyncapi.json + target/asyncapi-docs/index.html
// from AsyncApiChannels automatically on every `sbt compile`. Real GitHub
// Packages release - the meta-build needs its own resolver/credentials
// (build.sbt's don't apply here; this is project/, not the main build).
resolvers += "GitHub Packages" at "https://maven.pkg.github.com/beckfordp/purerest"

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env.getOrElse("GITHUB_ACTOR", ""),
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

// 0.2.0-dev adds schemaRegistryCheck (Rework 5 step 3/4, ADR 0003) - a
// real GitHub Packages release, same as 0.1.0-dev above.
addSbtPlugin("io.github.beckfordp" % "sbt-purekafka-docs" % "0.2.0-dev")
