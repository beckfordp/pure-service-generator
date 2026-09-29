val scala3Version = "3.9.0"

val circeVersion = "0.14.16"
val circeYamlVersion = "0.16.0"
val munitVersion = "1.3.6"

// Standalone sbt project, deliberately NOT aggregated into the root reference
// service's build - it's a dev-time tool that operates on a *generated* copy of
// this repo's g8 template, not a dependency of the service itself.
lazy val root = project
  .in(file("."))
  .settings(
    name := "field-codegen",
    scalaVersion := scala3Version,
    version := "0.1.0",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-yaml" % circeYamlVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )
