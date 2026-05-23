package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import tools.kaiju.gradlezilla.cli.format.GenerateFormatter
import tools.kaiju.gradlezilla.generator.DockerfileGenerator
import tools.kaiju.gradlezilla.inspector.GradleInspectorException
import tools.kaiju.gradlezilla.inspector.GradleProjectInspector
import java.io.File

class Generate :
    CliktCommand(
        help = "Generate Dockerfile for Android project",
        name = "generate",
    ) {
    private val projectDir: File by argument(
        help = "Path to the Android project root directory",
        name = "projectDir",
    ).file(
        mustExist = true,
        canBeFile = false,
        canBeDir = true,
    )

    private val dryRun by option(
        "--dry-run",
        "-d",
        help = "Print Dockerfile to console instead of writing to disk",
    ).flag(default = false)

    private val format by option(
        "--format",
        help = "Output format (human, json, sarif)",
    ).choice("human", "json", "sarif").default("human")

    @Suppress("SwallowedException")
    override fun run() {
        val isHuman = format == "human"

        if (isHuman) echo("Inspecting Android project at: ${projectDir.absolutePath} ...")

        val spec =
            try {
                GradleProjectInspector(projectDir).inspect()
            } catch (e: GradleInspectorException) {
                throw UsageError(e.message ?: "Could not connect to Gradle Project at '$projectDir'.")
            }

        val dockerfile = DockerfileGenerator().generate(spec)

        val outputPath: String? =
            if (dryRun) {
                null
            } else {
                val outputFile = File(projectDir, "Dockerfile")
                outputFile.writeText(dockerfile)
                outputFile.absolutePath
            }

        val formatter = GenerateFormatter.forFormat(format)
        echo(formatter.format(spec, dockerfile, outputPath))
    }
}
