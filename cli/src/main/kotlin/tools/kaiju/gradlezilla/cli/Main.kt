package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class Main : CliktCommand(
    help = "Dockerfile generator for Android",
    name = "gradlezilla",
) {
    override fun run() = Unit
}

fun main(args: Array<String>) = Main().subcommands(Version(), Inspect()).main(args)
