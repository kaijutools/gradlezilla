package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import tools.kaiju.gradlezilla.generator.DockerfileGenerator
import tools.kaiju.gradlezilla.inspector.GradleInspectorException
import tools.kaiju.gradlezilla.inspector.GradleProjectInspector
import tools.kaiju.gradlezilla.models.AndroidProjectSpec
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

    @Suppress("SwallowedException")
    override fun run() {
        val spec =
            try {
                GradleProjectInspector(projectDir).inspect()
            } catch (e: GradleInspectorException) {
                throw UsageError(e.message ?: "Could not connect to Gradle Project at '$projectDir'.")
            }

        echo("inspection\n${spec.format()}")

        val dockerfile = DockerfileGenerator().generate(spec)
        echo(dockerfile)
    }

    private fun AndroidProjectSpec.format(): String =
        buildList {
            add("JDK: $jdkVersion")
            add("CLI tools: $androidCommandLineToolsVersion")
            add("Android sdk: $androidSdkVersion")
            add("Platform tools: $androidPlatformToolsVersion")
            add("Ndk: $androidSdkVersion")
        }.joinToString("\n")
}
