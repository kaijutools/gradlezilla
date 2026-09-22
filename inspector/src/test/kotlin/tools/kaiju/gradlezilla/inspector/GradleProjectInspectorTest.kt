package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ModelBuilder
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.GradleProjectEnvironment
import tools.kaiju.gradlezilla.models.PinnedConnection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ThrowingExtractor : AgpDataExtractor {
    override val name = "ThrowingExtractor"

    override fun extract(context: ExtractionContext): ExtractionOutcome = error("boom")
}

private class SucceedingExtractor : AgpDataExtractor {
    override val name = "SucceedingExtractor"

    override fun extract(context: ExtractionContext): ExtractionOutcome =
        ExtractionOutcome.Found(AgpData(compileSdk = 34, buildToolsVersion = null, ndkVersion = null))
}

private class NotApplicableExtractor : AgpDataExtractor {
    override val name = "NotApplicableExtractor"

    override fun extract(context: ExtractionContext): ExtractionOutcome = ExtractionOutcome.NotApplicable("n/a")
}

private object FakePinnedConnection : PinnedConnection {
    override val gradleUserHome: File = File(".")

    override fun <T : Any?> model(type: Class<T>): ModelBuilder<T> = error("not used in this test")

    override fun <T : Any?> getModel(type: Class<T>): T = error("not used in this test")

    override fun <T : Any?> action(action: BuildAction<T>): BuildActionExecuter<T> = error("not used in this test")

    override fun build(): BuildLauncher = error("not used in this test")
}

private fun fakeContext(projectDir: File) =
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
    )

class GradleProjectInspectorTest {
    @Test
    fun executeExtractionChain_extractorThrows_continuesToNextExtractor() {
        val inspector =
            GradleProjectInspector(
                projectDir = File("."),
                extractors = listOf(ThrowingExtractor(), SucceedingExtractor()),
            )

        val result = inspector.executeExtractionChain(fakeContext(File(".")))

        assertEquals(34, result.compileSdk)
    }

    @Test
    fun executeExtractionChain_allExtractorsFail_aggregatesThrownMessage() {
        val inspector =
            GradleProjectInspector(
                projectDir = File("."),
                extractors = listOf(ThrowingExtractor(), NotApplicableExtractor()),
            )

        val exception =
            assertThrows {
                inspector.executeExtractionChain(fakeContext(File(".")))
            }

        assertTrue(exception.message!!.contains("ThrowingExtractor threw an unexpected error: boom"))
        assertTrue(exception.message!!.contains("NotApplicableExtractor: n/a"))
    }

    @Test
    fun executeExtractionChain_defaultExtractors_failureOnlyListsExtractorsThatRan() {
        val inspector = GradleProjectInspector(projectDir = File("."))

        val exception =
            assertThrows {
                inspector.executeExtractionChain(fakeContext(File(".")))
            }

        assertTrue(exception.message!!.contains("InitScriptExtractor"))
        assertTrue(exception.message!!.contains("VersionCatalogExtractor"))
        assertFalse(exception.message!!.contains("StaticBuildFileExtractor"))
    }

    private fun assertThrows(block: () -> Unit): GradleInspectorException {
        try {
            block()
        } catch (e: GradleInspectorException) {
            return e
        }
        error("Expected GradleInspectorException to be thrown")
    }
}
