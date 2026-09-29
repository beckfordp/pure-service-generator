package codegen

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Applies a field-spec to every file under a giter8-generated project
  * directory. Deliberately directory-agnostic about package/domain naming - it
  * scans for any regular file whose content contains a `codegen:fields:` marker
  * rather than hardcoding paths, since those vary per generation.
  */
object CodegenTool:

  private val skipDirNames = Set("target", ".git", ".bloop", ".metals", ".bsp")

  def runOn(projectDir: Path, fieldSpecPath: Path): Either[String, List[Path]] =
    for
      _ <- requireDirectory(projectDir)
      yamlText <- readFile(fieldSpecPath)
      spec <- FieldSpecParser.parse(yamlText)
      changed <- applyToProject(projectDir, spec.fields)
    yield changed

  private def requireDirectory(dir: Path): Either[String, Unit] =
    if Files.isDirectory(dir) then Right(())
    else Left(s"Project directory not found: $dir")

  private def readFile(path: Path): Either[String, String] =
    if !Files.isRegularFile(path) then Left(s"Field-spec file not found: $path")
    else Right(Files.readString(path))

  private def isUnderSkippedDir(projectDir: Path, path: Path): Boolean =
    projectDir
      .relativize(path)
      .iterator()
      .asScala
      .exists(segment => skipDirNames.contains(segment.toString))

  private def applyToProject(
      projectDir: Path,
      fields: List[Field]
  ): Either[String, List[Path]] =
    val candidates =
      Files
        .walk(projectDir)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .filterNot(isUnderSkippedDir(projectDir, _))
        .toList
        .sorted

    Try {
      candidates.flatMap { path =>
        val content = Files.readString(path)
        if content.contains("codegen:fields:") then
          val updated = AnchorTransformer.transform(content, fields)
          Files.writeString(path, updated)
          Some(path)
        else None
      }
    }.toEither.left.map {
      case e: IllegalArgumentException => e.getMessage
      case e                           => e.getMessage
    }
