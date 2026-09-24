package tools.kaiju.gradlezilla.inspector.versioncatalog

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.NativeBuildOutcome
import java.io.File
import java.io.IOException

class VersionCatalogExtractor : AgpDataExtractor {
    override val name: String
        get() = VersionCatalogExtractor::class.java.simpleName

    override fun extract(context: ExtractionContext): ExtractionOutcome {
        val catalogFile = File(context.projectDir, "gradle/libs.versions.toml")
        if (!catalogFile.exists()) {
            val reason = "Gradle Versions catalog file does not exist at ${catalogFile.absolutePath}"
            return ExtractionOutcome.NotApplicable(reason)
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
            val agpVersion = AGP_VERSION_KEYS.firstNotNullOfOrNull { versions[it] }

            ExtractionOutcome.Found(
                AgpData(
                    compileSdk = compileSdk,
                    buildToolsVersion = buildToolsVersion,
                    // A version-catalog entry such as ndk = "26.1.1" records a version, never
                    // whether any module configures externalNativeBuild — and only that decides
                    // whether an NDK is needed at all. This extractor runs without evaluating the
                    // build, so it cannot observe it and must not guess: emitting the catalog
                    // value here would reinstate, on the fallback path, exactly the false positive
                    // that was putting a ~1GB NDK into images for projects with no native code.
                    nativeBuild = NativeBuildOutcome.NotApplicable(NATIVE_BUILD_UNOBSERVABLE),
                    agpVersion = agpVersion,
                ),
            )
        } catch (e: IOException) {
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
        /** Named so the reason makes clear *which* extractor could not observe the build. */
        val NATIVE_BUILD_UNOBSERVABLE =
            "${VersionCatalogExtractor::class.java.simpleName} fallback cannot observe " +
                "externalNativeBuild without evaluating the build; NDK/CMake not emitted"

        val COMPILE_SDK_KEYS = listOf("compileSdk", "compile-sdk", "compileSdkVersion", "compile_sdk")
        val BUILD_TOOLS_KEYS = listOf("buildTools", "buildToolsVersion", "build-tools", "build_tools")
        val AGP_VERSION_KEYS = listOf("agp", "agp-version", "androidGradlePlugin")
    }
}
