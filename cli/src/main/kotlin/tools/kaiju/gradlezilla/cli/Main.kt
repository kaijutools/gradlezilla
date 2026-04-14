package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.terminal.Terminal

class Gradlezilla : CliktCommand(
    help = "Dockerfile generator for Android",
    name = "gradlezilla"
) {
    private val terminal = Terminal()

    override fun run() {
        terminal.println("rawwwwrrr!!!")
    }
}

fun main(args: Array<String>) = Gradlezilla().main(args)