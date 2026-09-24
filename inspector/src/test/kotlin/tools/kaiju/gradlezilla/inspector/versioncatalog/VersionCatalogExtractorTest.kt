package tools.kaiju.gradlezilla.inspector.versioncatalog

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ModelBuilder
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.GradleProjectEnvironment
import tools.kaiju.gradlezilla.models.NativeBuildOutcome
import tools.kaiju.gradlezilla.models.PinnedConnection
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object FakePinnedConnection : PinnedConnection {
    override val gradleUserHome: File = File(".")
    override val projectCacheDir: File = File(".")

    override fun <T : Any?> model(type: Class<T>): ModelBuilder<T> = error("not used in this test")

    override fun <T : Any?> getModel(type: Class<T>): T = error("not used in this test")

    override fun <T : Any?> action(action: BuildAction<T>): BuildActionExecuter<T> = error("not used in this test")

    override fun build(): BuildLauncher = error("not used in this test")
}

class VersionCatalogExtractorTest {
    private fun extract(catalog: String): ExtractionOutcome {
        val projectDir = Files.createTempDirectory("gradlezilla-catalog").toFile()
        File(projectDir, "gradle").mkdirs()
        File(projectDir, "gradle/libs.versions.toml").writeText(catalog)
        return try {
            // This extractor reads only the TOML file — it never touches the connection or the
            // environment, which is precisely why it cannot observe externalNativeBuild.
            VersionCatalogExtractor().extract(
                ExtractionContext(
                    projectDir = projectDir,
                    connection = FakePinnedConnection,
                    environment =
                        GradleProjectEnvironment(
                            gradleVersion = "8.0",
                            gradleJvmArgs = null,
                            modules = emptyList(),
                            hasBuildSrc = false,
                            hasBuildLogic = false,
                        ),
                ),
            )
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `an ndk entry in the catalog never becomes an emitted ndkVersion`() {
        // The whole point: a catalog records a version, not whether any module configures
        // externalNativeBuild — and this extractor cannot see the latter.
        val outcome =
            assertIs<ExtractionOutcome.Found>(
                extract(
                    """
                    [versions]
                    compileSdk = "34"
                    ndk = "26.1.10909125"
                    """.trimIndent(),
                ),
            )

        assertNull(outcome.data.ndkVersion)
        assertNull(outcome.data.cmakeVersion)
        assertTrue(outcome.data.nativeBuildWarnings.isEmpty())
    }

    @Test
    fun `a fallback extraction yields NotApplicable with a reason naming the fallback`() {
        val outcome =
            assertIs<ExtractionOutcome.Found>(
                extract(
                    """
                    [versions]
                    compileSdk = "34"
                    ndk = "26.1.10909125"
                    """.trimIndent(),
                ),
            )

        val nativeBuild = assertIs<NativeBuildOutcome.NotApplicable>(outcome.data.nativeBuild)
        assertTrue(
            nativeBuild.reason.contains(VersionCatalogExtractor::class.java.simpleName),
            "reason should name the fallback extractor, was: ${nativeBuild.reason}",
        )
        assertTrue(
            nativeBuild.reason.contains("externalNativeBuild"),
            "reason should say what it could not observe, was: ${nativeBuild.reason}",
        )
    }

    @Test
    fun `still extracts the fields it can actually observe`() {
        val outcome =
            assertIs<ExtractionOutcome.Found>(
                extract(
                    """
                    [versions]
                    compileSdk = "34"
                    buildTools = "34.0.0"
                    agp = "8.5.0"
                    ndk = "26.1.10909125"
                    """.trimIndent(),
                ),
            )

        assertEquals(34, outcome.data.compileSdk)
        assertEquals("34.0.0", outcome.data.buildToolsVersion)
        assertEquals("8.5.0", outcome.data.agpVersion)
        assertNull(outcome.data.ndkVersion)
    }
}
