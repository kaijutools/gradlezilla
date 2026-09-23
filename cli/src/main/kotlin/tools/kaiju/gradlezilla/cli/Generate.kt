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
import tools.kaiju.gradlezilla.generator.LayeredDockerfileGenerator
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

    private val layered by option(
        "--layered",
        help =
            "Generate a multi-layer Dockerfile that resolves Gradle dependencies in a cacheable " +
                "layer separate from application source, so source-only edits don't invalidate it",
    ).flag(default = false)

    private val format by option(
        "--format",
        help = "Output format (human, json, sarif)",
    ).choice("human", "json", "sarif").default("human")

    private val daemonJdk: File? by option(
        "--daemon-jdk",
        help = "JDK home to run the extraction daemon with, bypassing auto-discovery.",
    ).file(mustExist = true, canBeFile = false, canBeDir = true)

    @Suppress("SwallowedException")
    override fun run() {
        val isHuman = format == "human"

        if (isHuman) echo("Inspecting Android project at: ${projectDir.canonicalFile.absolutePath} ...")

        val spec =
            try {
                GradleProjectInspector(projectDir).inspect(daemonJdk)
            } catch (e: GradleInspectorException) {
                throw UsageError(e.message ?: "Could not connect to Gradle Project at '$projectDir'.")
            }

        val generator = if (layered) LayeredDockerfileGenerator() else DockerfileGenerator()
        val dockerfile = generator.generate(spec)

        val outputPath = resolveOutputPath(projectDir, dockerfile, dryRun)

        val formatter = GenerateFormatter.forFormat(format)
        echo(formatter.format(spec, dockerfile, outputPath))
    }
}

// Canonicalizes projectDir so a relative argument like "." doesn't leave a "/./" segment in outputPath.
internal fun resolveOutputPath(
    projectDir: File,
    dockerfile: String,
    dryRun: Boolean,
): String? {
    if (dryRun) return null

    val outputFile = File(projectDir.canonicalFile, "Dockerfile")
    outputFile.writeText(dockerfile)
    return outputFile.absolutePath
}
