package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import tools.kaiju.gradlezilla.cli.format.InspectFormatter
import tools.kaiju.gradlezilla.inspector.GradleInspectorException
import tools.kaiju.gradlezilla.inspector.GradleProjectInspector
import java.io.File

class Inspect :
    CliktCommand(
        name = "inspect",
        help = "List build targets (tasks) in a Gradle project.",
    ) {
    private val projectDir: File by argument(
        name = "projectDir",
        help = "Path to the Gradle project root directory.",
    ).file(mustExist = true, canBeFile = false, canBeDir = true)

    private val format by option(
        "--format",
        help = "Output format (human, json, sarif)",
    ).choice("human", "json", "sarif").default("human")

    @Suppress("SwallowedException")
    override fun run() {
        val targets =
            try {
                GradleProjectInspector(projectDir).targets()
            } catch (e: GradleInspectorException) {
                throw UsageError(e.message ?: "Could not connect to Gradle project at '$projectDir'")
            }

        if (targets.isEmpty()) {
            echo("No tasks found in $projectDir")
            return
        }

        echo(InspectFormatter.forFormat(format).format(targets))
    }
}
