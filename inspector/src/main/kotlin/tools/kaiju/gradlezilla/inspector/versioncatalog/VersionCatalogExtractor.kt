package tools.kaiju.gradlezilla.inspector.versioncatalog

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import java.io.File

class VersionCatalogExtractor : AgpDataExtractor {
    override val name: String
        get() = VersionCatalogExtractor::class.java.canonicalName

    override fun extract(context: ExtractionContext): ExtractionOutcome {
        val catalogFile = File(context.projectDir, "gradle/libs.versions.toml")
        if (!catalogFile.exists()) {
            return ExtractionOutcome.NotApplicable("Gradle Versions catalog file does not exist at ${catalogFile.absolutePath}")
        }

        return try {
            val versions = parseVersionCatalog(catalogFile)

            val compileSdk =
                COMPILE_SDK_KEYS.firstNotNullOfOrNull { versions[it]?.toIntOrNull() }
                    ?: return ExtractionOutcome.Failed(
                        reason =
                            "Could not determine compileSdk for project at '${context.projectDir}' — " +
                                "no compileSdk entry found in gradle/libs.versions.toml",
                        cause = null,
                    )

            val buildToolsVersion = BUILD_TOOLS_KEYS.firstNotNullOfOrNull { versions[it] }
            val ndkVersion = NDK_KEYS.firstNotNullOfOrNull { versions[it] }

            ExtractionOutcome.Found(
                AgpData(
                    compileSdk = compileSdk,
                    buildToolsVersion = buildToolsVersion,
                    ndkVersion = ndkVersion,
                ),
            )
        } catch (e: Exception) {
            ExtractionOutcome.Failed(e.message ?: "Failed to configure project", e)
        }
    }

    private fun parseVersionCatalog(catalogFile: File): Map<String, String> {
        val tomlReader =
            TomlFileReader(
                inputConfig =
                    TomlInputConfig(
                        ignoreUnknownNames = true,
                    ),
            )

        val versionCatalog =
            tomlReader.decodeFromFile<VersionCatalog>(
                deserializer = serializer(),
                tomlFilePath = catalogFile.absolutePath,
            )

        return versionCatalog.versions
    }

    private companion object {
        val COMPILE_SDK_KEYS = listOf("compileSdk", "compile-sdk", "compileSdkVersion", "compile_sdk")
        val BUILD_TOOLS_KEYS = listOf("buildTools", "buildToolsVersion", "build-tools", "build_tools")
        val NDK_KEYS = listOf("ndk", "ndkVersion", "ndk-version", "ndk_version", "androidNdk")
    }
}
