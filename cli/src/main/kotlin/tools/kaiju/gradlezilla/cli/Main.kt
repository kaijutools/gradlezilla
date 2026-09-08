package tools.kaiju.gradlezilla.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import tools.kaiju.gradlezilla.cli.format.ErrorFormatter
import kotlin.system.exitProcess

class Main :
    CliktCommand(
        help = "Dockerfile generator for Android",
        name = "gradlezilla",
    ) {
    override fun run() = Unit
}

/**
 * Which output format was requested, sniffed straight from argv, before Clikt parses anything.
 *
 * Errors thrown during/after Clikt's own parsing (bad option values, missing files, uncaught
 * exceptions from a command's run()) would otherwise always print as plain text — Clikt has no
 * concept of `--format` at the point it renders its own errors, and neither did this CLI before
 * a command's run() got a chance to pick a formatter. Knowing the format up front lets the
 * catch-blocks below format any of those failures consistently instead.
 */
internal fun detectFormat(args: Array<String>): String {
    for (i in args.indices) {
        val value =
            when {
                args[i] == "--format" && i + 1 < args.size -> args[i + 1]
                args[i].startsWith("--format=") -> args[i].removePrefix("--format=")
                else -> null
            }
        if (value == "json" || value == "sarif") return value
    }
    return "human"
}

@Suppress("TooGenericExceptionCaught")
fun main(args: Array<String>) {
    val command = Main().subcommands(Version(), Inspect(), Generate())
    val format = detectFormat(args)

    if (format == "human") {
        command.main(args)
        return
    }

    try {
        command.parse(args)
    } catch (e: PrintHelpMessage) {
        command.echoFormattedHelp(e)
        exitProcess(e.statusCode)
    } catch (e: PrintMessage) {
        println(e.message)
        exitProcess(e.statusCode)
    } catch (e: UsageError) {
        System.err.println(ErrorFormatter.forFormat(format).format(e.message ?: "Unknown error"))
        exitProcess(e.statusCode)
    } catch (e: CliktError) {
        exitProcess(e.statusCode)
    } catch (e: Exception) {
        System.err.println(ErrorFormatter.forFormat(format).format(e.message ?: e.toString()))
        exitProcess(1)
    }
}
