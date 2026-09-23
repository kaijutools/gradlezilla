package tools.kaiju.gradlezilla.inspector.initscript

import org.gradle.testkit.runner.GradleRunner
import tools.kaiju.gradlezilla.models.InitScriptOutputParser
import tools.kaiju.gradlezilla.models.JdkFactsParser
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class InitScriptGradleVersionCompatTest {
    /**
     * Pins the exact Gradle line (8.0) that regressed in #28: a bare top-level `providers` in
     * an init script resolves to `Gradle.getProviders()`, which `DefaultGradle` didn't expose
     * yet on 8.0/8.1 — so every extraction failed on this and every older supported Gradle
     * version. Running the real packaged extractor.gradle through TestKit against this exact
     * version turns that class of regression into a test failure instead of a silent extraction
     * failure in the field.
     */
    @Test
    fun extractorInitScript_runsCleanlyOnGradle8_0() {
        val projectDir = Files.createTempDirectory("gradlezilla-compat-test").toFile()
        try {
            projectDir.resolve("settings.gradle").writeText("rootProject.name = 'compat-probe'")
            projectDir.resolve("build.gradle").writeText("")

            val rawScript =
                javaClass.getResource("/extractor.gradle")?.readText()
                    ?: error("extractor.gradle not found on test classpath")
            val script =
                rawScript
                    .replace("{{PREFIX}}", InitScriptOutputParser.DATA_PREFIX)
                    .replace("{{JDK_PREFIX}}", JdkFactsParser.DATA_PREFIX)
            val initScriptFile = projectDir.resolve("init.gradle").apply { writeText(script) }

            val result =
                GradleRunner.create()
                    .withGradleVersion("8.0")
                    .withProjectDir(projectDir)
                    .withArguments("--init-script", initScriptFile.absolutePath, "help", "-q")
                    .build()

            // A script-evaluation failure (like the #28 regression) throws before this point;
            // this additionally confirms the emitJdk hook actually ran end to end.
            assertTrue(
                result.output.contains(JdkFactsParser.DATA_PREFIX),
                "expected init script to emit a JDK data line; got:\n${result.output}",
            )
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
