package tools.kaiju.gradlezilla.models

object InitScriptOutputParser {
    const val DATA_PREFIX = "GRADLEZILLA_AGP_DATA::"

    sealed class ParseOutcome {
        data class Success(
            val data: AgpData,
        ) : ParseOutcome()

        data object NoDataLine : ParseOutcome()

        data class MissingCompileSdk(
            val dataLine: String,
        ) : ParseOutcome()

        data class UnparseableCompileSdk(
            val rawValue: String,
        ) : ParseOutcome()
    }

    fun parse(output: String): ParseOutcome {
        val lines = output.lines().filter { it.startsWith(DATA_PREFIX) }

        if (lines.isEmpty()) return ParseOutcome.NoDataLine

        val dataLine = lines.first().removePrefix(DATA_PREFIX)

        val properties =
            dataLine.split("::").mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val (key, value) = parts
                key to value.takeIf { it != "null" }
            }.toMap()

        val rawSdk = properties["compileSdk"] ?: return ParseOutcome.MissingCompileSdk(dataLine)
        val compileSdk =
            rawSdk.substringAfterLast("-").toIntOrNull()
                ?: return ParseOutcome.UnparseableCompileSdk(rawSdk)

        return ParseOutcome.Success(
            AgpData(
                compileSdk = compileSdk,
                buildToolsVersion = properties["buildTools"],
                ndkVersion = properties["ndk"],
            ),
        )
    }
}
