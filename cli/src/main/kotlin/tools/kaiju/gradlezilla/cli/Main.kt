package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.terminal.Terminal

class Main : CliktCommand(
    help = "Dockerfile generator for Android",
    name = "gradlezilla",
) {
    init {
        versionOption("${BuildConfig.VERSION} (${BuildConfig.COMMIT_SHA})")
    }

    private val terminal = Terminal()

    override fun run() {
        terminal.println("rawwwwrrr!!!")
    }
}

fun main(args: Array<String>) = Main().main(args)
