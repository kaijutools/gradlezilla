package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractionException
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import java.io.File

class VersionCatalogExtractor : AgpDataExtractor {
    override fun extract(projectDir: File): AgpData? {
        val versions = parseVersionCatalog(projectDir)

        val compileSdk =
            COMPILE_SDK_KEYS.firstNotNullOfOrNull { versions[it]?.toIntOrNull() }
                ?: throw AgpDataExtractionException(
                    message =
                        "Could not determine compileSdk for project at '$projectDir' — " +
                            "no compileSdk entry found in gradle/libs.versions.toml",
                    cause = null,
                )

        val buildToolsVersion = BUILD_TOOLS_KEYS.firstNotNullOfOrNull { versions[it] }
        val ndkVersion = NDK_KEYS.firstNotNullOfOrNull { versions[it] }

        return AgpData(
            compileSdk = compileSdk,
            buildToolsVersion = buildToolsVersion,
            ndkVersion = ndkVersion,
        )
    }

    private fun parseVersionCatalog(projectDir: File): Map<String, String> {
        val catalog = File(projectDir, "gradle/libs.versions.toml").takeIf { it.exists() } ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        var inVersions = false
        for (line in catalog.readLines()) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    inVersions = true
                }

                trimmed.startsWith("[") -> {
                    inVersions = false
                }

                inVersions && !trimmed.startsWith("#") && trimmed.contains("=") -> {
                    val (rawKey, rawValue) = trimmed.split("=", limit = 2)
                    result[rawKey.trim()] = rawValue.trim().trim('"')
                }
            }
        }
        return result
    }

    private companion object {
        val COMPILE_SDK_KEYS = listOf("compileSdk", "compile-sdk", "compileSdkVersion", "compile_sdk")
        val BUILD_TOOLS_KEYS = listOf("buildTools", "buildToolsVersion", "build-tools", "build_tools")
        val NDK_KEYS = listOf("ndk", "ndkVersion", "ndk-version", "ndk_version", "androidNdk")
    }
}
