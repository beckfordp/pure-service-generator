package codegen

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

/** Applies a field-spec to every file under a giter8-generated project
  * directory. Deliberately directory-agnostic about package/domain naming - it
  * scans for any regular file whose content contains a `codegen:fields:` marker
  * rather than hardcoding paths, since those vary per generation.
  *
  * Exceptions are caught only at the genuine I/O boundary (reading/writing a
  * file); every other failure - an unrecognized anchor tag - is a typed `Left`
  * propagated from [[AnchorTransformer]], never an exception.
  */
object CodegenTool:

  private val skipDirNames = Set("target", ".git", ".bloop", ".metals", ".bsp")

  def runOn(projectDir: Path, fieldSpecPath: Path): Either[String, List[Path]] =
    for
      _ <- requireDirectory(projectDir)
      yamlText <- readFieldSpecFile(fieldSpecPath)
      spec <- FieldSpecParser.parse(yamlText)
      changed <- applyToProject(projectDir, spec.fields)
    yield changed

  private def requireDirectory(dir: Path): Either[String, Unit] =
    if Files.isDirectory(dir) then Right(())
    else Left(s"Project directory not found: $dir")

  private def readFieldSpecFile(path: Path): Either[String, String] =
    if !Files.isRegularFile(path) then Left(s"Field-spec file not found: $path")
    else readFile(path)

  private def readFile(path: Path): Either[String, String] =
    Try(Files.readString(path)).toEither.left.map(e =>
      s"Failed to read $path: ${e.getMessage}"
    )

  private def writeFile(path: Path, content: String): Either[String, Unit] =
    Try(Files.writeString(path, content)).toEither.left
      .map(e => s"Failed to write $path: ${e.getMessage}")
      .map(_ => ())

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
    Using.resource(Files.walk(projectDir)) { stream =>
      val candidates =
        stream
          .iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .filterNot(isUnderSkippedDir(projectDir, _))
          .toList
          .sorted

      candidates.foldLeft[Either[String, List[Path]]](Right(Nil)) {
        (acc, path) =>
          acc.flatMap { changedSoFar =>
            for
              content <- readFile(path)
              result <-
                if content.contains("codegen:fields:") then
                  for
                    updated <- AnchorTransformer.transform(content, fields)
                    _ <- writeFile(path, updated)
                  yield changedSoFar :+ path
                else Right(changedSoFar)
            yield result
          }
      }
    }
