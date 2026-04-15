package tools.kaiju.gradlezilla.inspector.versioncatalog

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractionException
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import java.io.File

class VersionCatalogExtractor : AgpDataExtractor {
    override fun extract(projectDir: File): AgpData? {
        val catalogFile = File(projectDir, "gradle/libs.versions.toml")
        if (!catalogFile.exists()) return null

        return try {
            val versions = parseVersionCatalog(catalogFile)

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
        } catch (e: Exception) {
            null
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

        println("Parsed version catalog from $catalogFile to $versionCatalog")

        return versionCatalog.versions
    }

    private companion object {
        val COMPILE_SDK_KEYS = listOf("compileSdk", "compile-sdk", "compileSdkVersion", "compile_sdk")
        val BUILD_TOOLS_KEYS = listOf("buildTools", "buildToolsVersion", "build-tools", "build_tools")
        val NDK_KEYS = listOf("ndk", "ndkVersion", "ndk-version", "ndk_version", "androidNdk")
    }
}
