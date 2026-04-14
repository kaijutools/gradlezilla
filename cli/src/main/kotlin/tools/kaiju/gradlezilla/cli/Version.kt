package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand

class Version : CliktCommand(
    help = """
        Shows version information about the current version.
    """.trimIndent(),
    name = "version",
) {
    override fun run() {
        echo(makeVersion())
    }

    internal fun makeVersion(): String {
        val semver = BuildConfig.VERSION
        val sha = BuildConfig.COMMIT_SHA
        val versionString = "Current version: $semver - $sha"
        return versionString
    }
}
