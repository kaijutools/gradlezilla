package tools.kaiju.gradlezilla.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinnedArgumentsTest {
    private val cacheDir = File("/tmp/gradlezilla-project-cache-test")

    private val allVersions = listOf(null, GradleVersion(5, 0), GradleVersion(6, 5), GradleVersion(9, 4))

    @Test
    fun buildPinnedArguments_alwaysIsolatesTheProjectCacheDir() {
        for (version in allVersions) {
            val args = buildPinnedArguments(cacheDir, version)
            assertEquals(
                listOf("--project-cache-dir", cacheDir.absolutePath),
                args.take(2),
                "project cache dir missing for Gradle $version",
            )
        }
    }

    @Test
    fun buildPinnedArguments_disablesIsolatedProjectsOnEveryVersion() {
        for (version in allVersions) {
            val args = buildPinnedArguments(cacheDir, version)
            assertTrue(
                args.contains("-D$ISOLATED_PROJECTS_PROPERTY=false"),
                "Isolated Projects opt-out missing for Gradle $version: $args",
            )
        }
    }

    @Test
    fun buildPinnedArguments_disablesConfigurationCacheOnGradle6_6AndLater() {
        for (version in listOf(GradleVersion(6, 6), GradleVersion(8, 0), GradleVersion(9, 4))) {
            assertTrue(
                buildPinnedArguments(cacheDir, version).contains("--no-configuration-cache"),
                "expected --no-configuration-cache on Gradle $version",
            )
        }
    }

    /**
     * The option did not exist before Gradle 6.6 and an unknown command-line option *fails the
     * build* — verified against real 6.0 and 6.5 distributions. Target support goes back to 5.0.
     */
    @Test
    fun buildPinnedArguments_omitsConfigurationCacheFlagBeforeGradle6_6() {
        for (version in listOf(GradleVersion(5, 0), GradleVersion(6, 0), GradleVersion(6, 5))) {
            assertFalse(
                buildPinnedArguments(cacheDir, version).contains("--no-configuration-cache"),
                "--no-configuration-cache would fail the build on Gradle $version",
            )
        }
    }

    /** Version probe failed or returned something unparseable — omit the flag rather than risk it. */
    @Test
    fun buildPinnedArguments_omitsConfigurationCacheFlagForUnknownVersion() {
        assertFalse(buildPinnedArguments(cacheDir, null).contains("--no-configuration-cache"))
    }

    /** The per-run cache-bust token is gone; nothing here may vary between calls. */
    @Test
    fun buildPinnedArguments_areStableAcrossCalls() {
        assertEquals(
            buildPinnedArguments(cacheDir, GradleVersion(9, 4)),
            buildPinnedArguments(cacheDir, GradleVersion(9, 4)),
        )
    }
}
