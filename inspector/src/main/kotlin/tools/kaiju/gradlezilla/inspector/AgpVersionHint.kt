package tools.kaiju.gradlezilla.inspector

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import tools.kaiju.gradlezilla.inspector.versioncatalog.VersionCatalog
import tools.kaiju.gradlezilla.models.AgpVersion
import java.io.File

/**
 * A best-effort AGP version straight from the version catalog file, without a Gradle
 * connection — needed to compute the daemon JDK floor *before* one exists to connect with.
 * [tools.kaiju.gradlezilla.inspector.versioncatalog.VersionCatalogExtractor] does the
 * authoritative extraction post-connection for spec.jdkVersion; this is deliberately not
 * shared with it, since that extractor is shaped around a live [ExtractionContext] this code
 * runs before one exists.
 */
object AgpVersionHint {
    private val AGP_VERSION_KEYS = listOf("agp", "agp-version", "androidGradlePlugin")

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun fromVersionCatalog(projectDir: File): AgpVersion? {
        val catalogFile = File(projectDir, "gradle/libs.versions.toml").takeIf { it.isFile } ?: return null
        return try {
            val versions =
                TomlFileReader(inputConfig = TomlInputConfig(ignoreUnknownNames = true))
                    .decodeFromFile<VersionCatalog>(
                        deserializer = serializer(),
                        tomlFilePath = catalogFile.absolutePath,
                    ).versions
            AGP_VERSION_KEYS.firstNotNullOfOrNull { versions[it] }?.let(AgpVersion::parse)
        } catch (e: Exception) {
            null
        }
    }
}
