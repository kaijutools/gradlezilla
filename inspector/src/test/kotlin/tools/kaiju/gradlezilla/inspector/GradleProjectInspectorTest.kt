package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome
import tools.kaiju.gradlezilla.models.GradleProjectEnvironment
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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

private object FakeProjectConnection : ProjectConnection {
    override fun <T : Any?> getModel(viewType: Class<T>): T = error("not used in this test")

    override fun <T : Any?> getModel(
        viewType: Class<T>,
        handler: ResultHandler<in T>,
    ) = error("not used in this test")

    override fun newBuild() = error("not used in this test")

    override fun newTestLauncher() = error("not used in this test")

    override fun <T : Any?> model(modelType: Class<T>) = error("not used in this test")

    override fun <T : Any?> action(buildAction: org.gradle.tooling.BuildAction<T>) = error("not used in this test")

    override fun action(): BuildActionExecuter.Builder = error("not used in this test")

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<Path>) = error("not used in this test")

    override fun close() = Unit
}

private fun fakeContext(projectDir: File) =
    ExtractionContext(
        projectDir = projectDir,
        connection = FakeProjectConnection,
        environment =
            GradleProjectEnvironment(
                jdkVersion = 17,
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

    private fun assertThrows(block: () -> Unit): GradleInspectorException {
        try {
            block()
        } catch (e: GradleInspectorException) {
            return e
        }
        error("Expected GradleInspectorException to be thrown")
    }
}
