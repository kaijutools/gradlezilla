package tools.kaiju.gradlezilla.models

import org.gradle.tooling.ProjectConnection
import java.io.File

data class ExtractionContext(
    val projectDir: File,
    val connection: ProjectConnection,
    val environment: GradleProjectEnvironment,
)

sealed class ExtractionOutcome {
    data class Found(
        val data: AgpData,
    ) : ExtractionOutcome()

    data class NotApplicable(
        val reason: String,
    ) : ExtractionOutcome()

    data class Failed(
        val reason: String,
        val cause: Throwable?,
    ) : ExtractionOutcome()
}

interface AgpDataExtractor {
    val name: String

    @Throws(AgpDataExtractionException::class)
    fun extract(context: ExtractionContext): ExtractionOutcome
}
