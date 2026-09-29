package codegen

import munit.FunSuite

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class CodegenToolSuite extends FunSuite {

  private val fieldSpecYaml =
    """fields:
      |  - name: color
      |    type: String
      |    example: "red"
      |""".stripMargin

  private val storeFixture =
    """final case class Widget(
      |    id: String,
      |    status: String,
      |    // codegen:fields:CASE_CLASS_FIELD
      |    createdAt: java.time.Instant
      |)
      |""".stripMargin

  private val untouchedFixture =
    "object NothingToSeeHere { val x = 1 }\n"

  private def tempDir(): Path = Files.createTempDirectory("codegen-tool-suite")

  private def deleteRecursively(root: Path): Unit =
    if Files.exists(root) then
      Files
        .walk(root)
        .iterator()
        .asScala
        .toList
        .reverse
        .foreach(Files.deleteIfExists)

  private def writeFile(path: Path, content: String): Unit =
    Files.createDirectories(path.getParent)
    Files.writeString(path, content)

  test(
    "rewrites every file under the project dir that contains a codegen:fields anchor"
  ) {
    val projectDir = tempDir()
    try {
      val storeFile =
        projectDir.resolve("src/main/scala/widgetservice/WidgetStore.scala")
      val untouchedFile =
        projectDir.resolve("src/main/scala/widgetservice/Main.scala")
      writeFile(storeFile, storeFixture)
      writeFile(untouchedFile, untouchedFixture)

      val specFile = projectDir.resolve("field-spec.yaml")
      writeFile(specFile, fieldSpecYaml)

      val result = CodegenTool.runOn(projectDir, specFile)
      assert(result.isRight, s"expected Right, got: $result")
      val changed = result.toOption.get
      assertEquals(changed, List(storeFile))

      val storeContent = Files.readString(storeFile)
      assert(
        clue(storeContent).contains(
          "    color: String,\n    createdAt: java.time.Instant"
        ),
        "expected the new field to be inserted before createdAt"
      )
      assertEquals(Files.readString(untouchedFile), untouchedFixture)
    } finally deleteRecursively(projectDir)
  }

  test(
    "skips files under target/ even if they happen to contain the anchor marker text"
  ) {
    val projectDir = tempDir()
    try {
      val buildArtifact =
        projectDir.resolve("target/scala-3.9.0/classes/Leftover.scala")
      writeFile(buildArtifact, storeFixture)

      val specFile = projectDir.resolve("field-spec.yaml")
      writeFile(specFile, fieldSpecYaml)

      val result = CodegenTool.runOn(projectDir, specFile)
      assert(result.isRight, s"expected Right, got: $result")
      assertEquals(result.toOption.get, Nil)
      assertEquals(Files.readString(buildArtifact), storeFixture)
    } finally deleteRecursively(projectDir)
  }

  test("reports an error for a missing field-spec file") {
    val projectDir = tempDir()
    try {
      val result =
        CodegenTool.runOn(projectDir, projectDir.resolve("does-not-exist.yaml"))
      assert(result.isLeft, s"expected Left, got: $result")
    } finally deleteRecursively(projectDir)
  }

  test("reports an error for a missing project directory") {
    val projectDir = tempDir()
    try {
      val specFile = projectDir.resolve("field-spec.yaml")
      writeFile(specFile, fieldSpecYaml)
      val result =
        CodegenTool.runOn(projectDir.resolve("no-such-dir"), specFile)
      assert(result.isLeft, s"expected Left, got: $result")
    } finally deleteRecursively(projectDir)
  }

  test(
    "reports an error rather than partially rewriting when an anchor tag is unrecognized"
  ) {
    val projectDir = tempDir()
    try {
      val badFile = projectDir.resolve("Bad.scala")
      writeFile(
        badFile,
        "def x(/* codegen:fields:NOT_A_REAL_TAG */): Unit = ()\n"
      )
      val specFile = projectDir.resolve("field-spec.yaml")
      writeFile(specFile, fieldSpecYaml)

      val result = CodegenTool.runOn(projectDir, specFile)
      assert(result.isLeft, s"expected Left, got: $result")
    } finally deleteRecursively(projectDir)
  }
}
