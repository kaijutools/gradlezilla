package tools.kaiju.gradlezilla.models

object JdkFactsParser {
    const val DATA_PREFIX = "GRADLEZILLA_JDK_DATA::"

    fun parse(output: String): List<ModuleJdkFacts> =
        output.lines()
            .filter { it.startsWith(DATA_PREFIX) }
            .mapNotNull { parseLine(it.removePrefix(DATA_PREFIX)) }

    private fun parseLine(dataLine: String): ModuleJdkFacts? {
        val properties =
            dataLine.split("::").mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val (key, value) = parts
                key to value.takeIf { it != "null" }
            }.toMap()

        val modulePath = properties["path"] ?: return null

        return ModuleJdkFacts(
            modulePath = modulePath,
            toolchainVersion = properties["toolchain"]?.toIntOrNull(),
            kotlinToolchainVersion = properties["kotlinToolchain"]?.toIntOrNull(),
            javaTargetVersion = properties["javaTarget"]?.toIntOrNull(),
            kotlinTargetVersion = properties["kotlinTarget"]?.toIntOrNull(),
        )
    }
}
