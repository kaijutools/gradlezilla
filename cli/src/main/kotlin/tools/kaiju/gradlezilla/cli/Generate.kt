package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
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
        echo("do the dew")
    }
}
