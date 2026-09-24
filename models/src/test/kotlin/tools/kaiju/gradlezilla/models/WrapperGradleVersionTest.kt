package tools.kaiju.gradlezilla.models

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WrapperGradleVersionTest {
    private fun projectWithWrapper(distributionUrl: String?): File {
        val dir = Files.createTempDirectory("gradlezilla-wrapper-test").toFile()
        if (distributionUrl != null) {
            val wrapper = File(dir, "gradle/wrapper").apply { mkdirs() }
            File(wrapper, "gradle-wrapper.properties").writeText("distributionUrl=$distributionUrl\n")
        }
        return dir
    }

    private fun <T> withProject(
        distributionUrl: String?,
        block: (File) -> T,
    ): T {
        val dir = projectWithWrapper(distributionUrl)
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrapperGradleVersion_readsTheDistributionUrl() {
        withProject("https\\://services.gradle.org/distributions/gradle-8.0.2-all.zip") { dir ->
            assertEquals(GradleVersion(8, 0), wrapperGradleVersion(dir))
        }
    }

    /** Signal-Android's line, and the oldest version in the corpus that still gets the flag. */
    @Test
    fun wrapperGradleVersion_readsABinDistribution() {
        withProject("https\\://services.gradle.org/distributions/gradle-6.5-bin.zip") { dir ->
            assertEquals(GradleVersion(6, 5), wrapperGradleVersion(dir))
        }
    }

    @Test
    fun wrapperGradleVersion_isNullWithoutAWrapper() {
        withProject(null) { dir -> assertNull(wrapperGradleVersion(dir)) }
    }

    @Test
    fun wrapperGradleVersion_isNullForAnUnparseableDistributionUrl() {
        withProject("https\\://example.invalid/some-tarball.zip") { dir ->
            assertNull(wrapperGradleVersion(dir))
        }
    }
}
