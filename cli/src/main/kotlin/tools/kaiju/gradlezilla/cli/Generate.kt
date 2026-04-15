package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
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

    override fun run() {
        val inspection =
            try {
                GradleProjectInspector(projectDir).inspect()
            } catch (e: GradleInspectorException) {
                throw UsageError(e.message ?: "Could not connect to Gradle Project at '$projectDir'.")
            }

        echo("inspection = $inspection")
    }
}
