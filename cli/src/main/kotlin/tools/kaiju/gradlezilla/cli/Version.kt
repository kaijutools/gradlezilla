package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand

class Version : CliktCommand(
    help = """
        Shows version information about the current version.
    """.trimIndent(),
    name = "version",
) {
    override fun run() {
        val semver = BuildConfig.VERSION
        val sha = BuildConfig.COMMIT_SHA
        echo("Current version: $semver - $sha")
    }
}