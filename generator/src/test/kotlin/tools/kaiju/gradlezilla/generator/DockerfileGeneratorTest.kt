package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec
import tools.kaiju.gradlezilla.models.ModuleSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerfileGeneratorTest {
    private val generator = DockerfileGenerator()

    private val baseSpec =
        AndroidProjectSpec(
            jdkVersion = 17,
            androidSdkVersion = 34,
            androidPlatformToolsVersion = "34.0.5",
            androidCommandLineToolsVersion = "11076708",
        )

    private fun render(spec: AndroidProjectSpec = baseSpec): String = generator.generate(spec).render()

    // ── generate() preconditions ──────────────────────────────────────────

    @Test
    fun `generate throws when androidCommandLineToolsVersion is null`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate(baseSpec.copy(androidCommandLineToolsVersion = null))
        }
    }

    // ── Dockerfile structure ──────────────────────────────────────────────

    @Test
    fun `rendered output starts with dockerfile syntax directive`() {
        assertTrue(render().startsWith("# syntax=docker/dockerfile:1"))
    }

    @Test
    fun `generate produces eight layers`() {
        assertEquals(8, generator.generate(baseSpec).layers.size)
    }

    // ── L1: Base image ────────────────────────────────────────────────────

    @Test
    fun `base image uses jdk version`() {
        assertTrue(render().contains("FROM eclipse-temurin:17-jdk-jammy"))
    }

    @Test
    fun `base image reflects custom jdk version`() {
        assertTrue(render(baseSpec.copy(jdkVersion = 21)).contains("FROM eclipse-temurin:21-jdk-jammy"))
    }

    // ── ENV vars ──────────────────────────────────────────────────────────

    @Test
    fun `env sets ANDROID_HOME`() {
        assertTrue(render().contains("ANDROID_HOME=/opt/android-sdk"))
    }

    @Test
    fun `env sets PATH with cmdline-tools and platform-tools`() {
        val output = render()
        assertTrue(output.contains("cmdline-tools/latest/bin"))
        assertTrue(output.contains("platform-tools"))
    }

    @Test
    fun `env sets GRADLE_OPTS daemon false`() {
        assertTrue(render().contains("GRADLE_OPTS=\"-Dorg.gradle.daemon=false\""))
    }

    @Test
    fun `env appends gradleJvmArgs when provided`() {
        val output = render(baseSpec.copy(gradleJvmArgs = "-Xmx4g -XX:+UseG1GC"))
        assertTrue(output.contains("-Xmx4g -XX:+UseG1GC"))
    }

    @Test
    fun `env does not mention jvm args when gradleJvmArgs is null`() {
        assertFalse(render().contains("JVM args"))
    }

    // ── L2: cmdline-tools ─────────────────────────────────────────────────

    @Test
    fun `cmdline-tools download url contains commandLineToolsVersion`() {
        assertTrue(render().contains("commandlinetools-linux-11076708_latest.zip"))
    }

    @Test
    fun `cmdline-tools download uses curl`() {
        assertTrue(render().contains("curl"))
    }

    // ── L3: SDK packages ──────────────────────────────────────────────────

    @Test
    fun `sdk packages include platforms for androidSdkVersion`() {
        assertTrue(render().contains("platforms;android-34"))
    }

    @Test
    fun `sdk packages include build-tools for androidPlatformToolsVersion`() {
        assertTrue(render().contains("build-tools;34.0.5"))
    }

    @Test
    fun `sdk packages include platform-tools`() {
        assertTrue(render().contains("\"platform-tools\""))
    }

    @Test
    fun `sdk packages include ndk when androidNdkVersion is set`() {
        val output = render(baseSpec.copy(androidNdkVersion = "25.1.8937393"))
        assertTrue(output.contains("ndk;25.1.8937393"))
    }

    @Test
    fun `sdk packages omit ndk when androidNdkVersion is null`() {
        assertFalse(render().contains("ndk;"))
    }

    @Test
    fun `sdk packages include cmake when androidCmakeVersion is set`() {
        val output = render(baseSpec.copy(androidCmakeVersion = "3.22.1"))
        assertTrue(output.contains("cmake;3.22.1"))
    }

    @Test
    fun `sdk packages omit cmake when androidCmakeVersion is null`() {
        assertFalse(render().contains("cmake;"))
    }

    // ── L5: Dependency resolution ─────────────────────────────────────────

    @Test
    fun `dependency resolution copies buildSrc when hasBuildSrc is true`() {
        val output = render(baseSpec.copy(hasBuildSrc = true))
        assertTrue(output.contains("COPY buildSrc/"))
    }

    @Test
    fun `dependency resolution omits buildSrc when hasBuildSrc is false`() {
        assertFalse(render().contains("COPY buildSrc/"))
    }

    @Test
    fun `dependency resolution copies build-logic when hasBuildLogic is true`() {
        val output = render(baseSpec.copy(hasBuildLogic = true))
        assertTrue(output.contains("COPY build-logic/"))
    }

    @Test
    fun `dependency resolution copies per-module build files`() {
        val spec =
            baseSpec.copy(
                modules =
                    listOf(
                        ModuleSpec(path = ":app", isApplication = true),
                        ModuleSpec(path = ":feature:login"),
                    ),
            )
        val output = render(spec)
        assertTrue(output.contains("COPY app/build.gradle"))
        assertTrue(output.contains("COPY feature/login/build.gradle"))
    }

    // ── Header comment ────────────────────────────────────────────────────

    @Test
    fun `header comment includes compileSdk`() {
        assertTrue(render().contains("compileSdk:    34"))
    }

    @Test
    fun `header comment includes ndk version when set`() {
        val output = render(baseSpec.copy(androidNdkVersion = "25.1.8937393"))
        assertTrue(output.contains("ndk:           25.1.8937393"))
    }

    @Test
    fun `header comment lists module paths`() {
        val spec =
            baseSpec.copy(
                modules = listOf(ModuleSpec(path = ":app", isApplication = true)),
            )
        assertTrue(render(spec).contains("module:        :app (app)"))
    }
}
