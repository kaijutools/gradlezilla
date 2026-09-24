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
        val properties = properties(dataLine)

        val rawSdk = properties["compileSdk"] ?: return ParseOutcome.MissingCompileSdk(dataLine)
        val compileSdk =
            rawSdk.substringAfterLast("-").toIntOrNull()
                ?: return ParseOutcome.UnparseableCompileSdk(rawSdk)

        // Native config is resolved across *every* Android module, not just the first: only some
        // of them build native code, and the one that does need not be the first one emitted.
        val moduleProperties = lines.map { properties(it.removePrefix(DATA_PREFIX)) }
        val native =
            NativeBuildResolver.resolve(
                modules = moduleProperties.map(::toModuleNativeBuild),
                agpDefaultCmakeVersion = moduleProperties.firstNotNullOfOrNull { it["defaultCmake"] },
            )

        return ParseOutcome.Success(
            AgpData(
                compileSdk = compileSdk,
                buildToolsVersion = properties["buildTools"],
                ndkVersion = (native as? NativeBuildOutcome.Found)?.ndkVersion,
                agpVersion = properties["agpVersion"],
                cmakeVersion = (native as? NativeBuildOutcome.Found)?.cmakeVersion,
                nativeBuildWarnings = (native as? NativeBuildOutcome.Found)?.warnings.orEmpty(),
            ),
        )
    }

    private fun toModuleNativeBuild(properties: Map<String, String>): ModuleNativeBuild {
        val kinds = properties["nativeBuild"]?.split("+").orEmpty()
        return ModuleNativeBuild(
            path = properties["path"] ?: "<unknown>",
            usesCmake = "cmake" in kinds,
            usesNdkBuild = "ndkBuild" in kinds,
            ndkVersion = properties["ndk"],
            cmakeVersion = properties["cmakeVersion"],
        )
    }

    private fun properties(dataLine: String): Map<String, String> =
        dataLine.split("::").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val (key, value) = parts
            key to value.takeIf { it != "null" }
        }.toMap().filterValues { it != null }.mapValues { it.value!! }
}
