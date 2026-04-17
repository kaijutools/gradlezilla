package tools.kaiju.gradlezilla.generator

import tools.kaiju.gradlezilla.models.AndroidProjectSpec
import kotlin.test.Test
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

    // Our generator now returns a raw String directly
    private fun render(spec: AndroidProjectSpec = baseSpec): String = generator.generate(spec)

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

    // ── L2: cmdline-tools ─────────────────────────────────────────────────

    @Test
    fun `cmdline-tools download url contains commandLineToolsVersion`() {
        assertTrue(render().contains("commandlinetools-linux-11076708_latest.zip"))
    }

    @Test
    fun `cmdline-tools defaults to fallback when null`() {
        // Our refactor gracefully falls back instead of throwing an exception
        val output = render(baseSpec.copy(androidCommandLineToolsVersion = null))
        assertTrue(output.contains("commandlinetools-linux-11076708_latest.zip"))
    }

    @Test
    fun `cmdline-tools download uses wget`() {
        assertTrue(render().contains("wget -q https://dl.google.com"))
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
    fun `sdk packages default build-tools when null`() {
        val output = render(baseSpec.copy(androidPlatformToolsVersion = null))
        assertTrue(output.contains("build-tools;34.0.0"))
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

    // ── L4: MVP Flat Execution ────────────────────────────────────────────

    @Test
    fun `build execution uses flat copy`() {
        assertTrue(render().contains("COPY . ."))
    }

    @Test
    fun `build execution uses default assembleRelease command`() {
        assertTrue(render().contains("CMD [\"bash\", \"-c\", \"./gradlew assembleRelease --no-daemon\"]"))
    }
}
