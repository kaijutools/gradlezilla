package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec
import tools.kaiju.gradlezilla.models.ModuleSpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayeredDockerfileGeneratorTest {
    private val generator = LayeredDockerfileGenerator()

    private val baseSpec =
        AndroidProjectSpec(
            jdkVersion = 17,
            androidSdkVersion = 34,
            androidBuildToolsVersion = "34.0.5",
            androidCommandLineToolsVersion = "11076708",
        )

    private fun render(spec: AndroidProjectSpec = baseSpec): String = generator.generate(spec)

    // ── Base image / SDK setup (shared with the flat generator) ────────────

    @Test
    fun `base image uses jdk version`() {
        assertTrue(render().contains("FROM eclipse-temurin:17-jdk-jammy"))
    }

    @Test
    fun `sdk packages include platforms and build-tools`() {
        val output = render()
        assertTrue(output.contains("platforms;android-34"))
        assertTrue(output.contains("build-tools;34.0.5"))
    }

    // ── Dependency resolution layer ─────────────────────────────────────────

    @Test
    fun `copies gradle wrapper and build files before source`() {
        val output = render()
        val wrapperCopyIndex = output.indexOf("COPY gradlew gradlew")
        val buildFilesCopyIndex = output.indexOf("COPY build.gradle* settings.gradle* gradle.properties* ./")
        val fullCopyIndex = output.indexOf("COPY . .")

        assertTrue(wrapperCopyIndex >= 0)
        assertTrue(buildFilesCopyIndex > wrapperCopyIndex)
        assertTrue(fullCopyIndex > buildFilesCopyIndex)
    }

    @Test
    fun `warms dependency cache before copying full source`() {
        val output = render()
        val warmupIndex = output.indexOf("RUN ./gradlew --no-daemon dependencies || true")
        val fullCopyIndex = output.indexOf("COPY . .")

        assertTrue(warmupIndex >= 0)
        assertTrue(fullCopyIndex > warmupIndex)
    }

    @Test
    fun `single module project warms up root dependencies task`() {
        assertTrue(render().contains("RUN ./gradlew --no-daemon dependencies || true"))
    }

    @Test
    fun `multi module project copies each module build file and warms its dependencies task`() {
        val spec =
            baseSpec.copy(
                modules =
                    listOf(
                        ModuleSpec(path = ":app", isApplication = true),
                        ModuleSpec(path = ":feature:foo"),
                    ),
            )
        val output = render(spec)

        assertTrue(output.contains("COPY app/build.gradle* app/"))
        assertTrue(output.contains("COPY feature/foo/build.gradle* feature/foo/"))
        assertTrue(output.contains("RUN ./gradlew --no-daemon :app:dependencies :feature:foo:dependencies || true"))
    }

    @Test
    fun `copies buildSrc when present`() {
        assertTrue(render(baseSpec.copy(hasBuildSrc = true)).contains("COPY buildSrc buildSrc"))
        assertFalse(render(baseSpec.copy(hasBuildSrc = false)).contains("COPY buildSrc buildSrc"))
    }

    @Test
    fun `copies build-logic when present`() {
        assertTrue(render(baseSpec.copy(hasBuildLogic = true)).contains("COPY build-logic build-logic"))
        assertFalse(render(baseSpec.copy(hasBuildLogic = false)).contains("COPY build-logic build-logic"))
    }

    // ── Build execution layer ────────────────────────────────────────────

    @Test
    fun `build execution uses default assembleRelease command`() {
        assertTrue(render().contains("CMD [\"bash\", \"-c\", \"./gradlew assembleRelease --no-daemon\"]"))
    }
}
