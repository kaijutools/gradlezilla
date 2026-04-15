package tools.kaiju.gradlezilla.inspector

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import java.io.File

class GradleProjectInspector(
    private val projectDir: File,
) {
    fun targets(): List<BuildTarget> {
        try {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .connect()
                .use { connection ->
                    val project = connection.getModel(GradleProject::class.java)
                    return project.tasks
                        .map { task ->
                            BuildTarget(
                                name = task.name,
                                path = task.path,
                                group = task.group?.takeIf { it.isNotBlank() },
                                description = task.description?.takeIf { it.isNotBlank() },
                            )
                        }.sortedWith(compareBy({ it.group ?: "\uFFFF" }, { it.path }))
                }
        } catch (e: GradleConnectionException) {
            throw GradleInspectorException("Could not connect to Gradle project at '$projectDir': ${e.message}", e)
        }
    }
}
