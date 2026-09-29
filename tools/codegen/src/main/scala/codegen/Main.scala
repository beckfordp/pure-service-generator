package codegen

import java.nio.file.Paths

object Main:

  def main(args: Array[String]): Unit =
    args.toList match
      case projectDirArg :: fieldSpecArg :: Nil =>
        CodegenTool.runOn(
          Paths.get(projectDirArg),
          Paths.get(fieldSpecArg)
        ) match
          case Right(changed) =>
            println(
              s"field-codegen: applied field spec to ${changed.size} file(s):"
            )
            changed.foreach(p => println(s"  $p"))
          case Left(error) =>
            System.err.println(s"field-codegen: $error")
            sys.exit(1)
      case _ =>
        System.err.println(
          "Usage: field-codegen <generated-project-dir> <field-spec.yaml>"
        )
        sys.exit(1)
