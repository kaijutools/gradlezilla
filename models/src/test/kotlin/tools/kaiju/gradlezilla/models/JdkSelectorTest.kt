package tools.kaiju.gradlezilla.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JdkSelectorTest {
    private fun candidate(
        path: String,
        version: Int,
        source: JdkSource,
    ) = JdkCandidate(File(path), version, source)

    @Test
    fun `prefers highest LTS within the window over a higher non-LTS version`() {
        val candidates =
            listOf(
                candidate("/jdk24", 24, JdkSource.SYSTEM_PATH),
                candidate("/jdk21", 21, JdkSource.SYSTEM_PATH),
                candidate("/jdk17", 17, JdkSource.GRADLE_JDKS),
            )

        val result = JdkSelector.select(candidates, JdkWindow(floor = null, ceiling = null))

        assertIs<JdkSelectionResult.Selected>(result)
        assertEquals(21, result.version)
    }

    @Test
    fun `excludes candidates outside the window`() {
        val candidates =
            listOf(
                candidate("/jdk26", 26, JdkSource.SYSTEM_PATH),
                candidate("/jdk17", 17, JdkSource.GRADLE_JDKS),
                candidate("/jdk8", 8, JdkSource.SDKMAN),
            )

        // Sunflower-shaped window: AGP floor 17, Gradle 8.1 ceiling 19.
        val result = JdkSelector.select(candidates, JdkWindow(floor = 17, ceiling = 19))

        assertIs<JdkSelectionResult.Selected>(result)
        assertEquals(17, result.version)
        assertEquals(JdkSource.GRADLE_JDKS, result.source)
    }

    @Test
    fun `explicit JAVA_HOME wins even when a higher LTS is also compatible`() {
        val candidates =
            listOf(
                candidate("/jdk17", 17, JdkSource.JAVA_HOME),
                candidate("/jdk21", 21, JdkSource.GRADLE_JDKS),
            )

        val result = JdkSelector.select(candidates, JdkWindow(floor = null, ceiling = null))

        assertIs<JdkSelectionResult.Selected>(result)
        assertEquals(17, result.version)
        assertEquals(JdkSource.JAVA_HOME, result.source)
    }

    @Test
    fun `incompatible JAVA_HOME is skipped in favor of another compatible candidate`() {
        val candidates =
            listOf(
                candidate("/jdk21", 21, JdkSource.JAVA_HOME),
                candidate("/jdk17", 17, JdkSource.GRADLE_JDKS),
            )

        val result = JdkSelector.select(candidates, JdkWindow(floor = null, ceiling = 19))

        assertIs<JdkSelectionResult.Selected>(result)
        assertEquals(17, result.version)
        assertEquals(JdkSource.GRADLE_JDKS, result.source)
    }

    @Test
    fun `no candidate in window reports NoCompatibleJdk with the candidates and range`() {
        val candidates = listOf(candidate("/jdk21", 21, JdkSource.SYSTEM_PATH))
        val window = JdkWindow(floor = 17, ceiling = 19)

        val result = JdkSelector.select(candidates, window)

        assertIs<JdkSelectionResult.NoCompatibleJdk>(result)
        assertEquals(candidates, result.candidatesConsidered)
        assertEquals(window, result.requiredRange)
    }

    @Test
    fun `empty candidate list reports NoCompatibleJdk`() {
        val result = JdkSelector.select(emptyList(), JdkWindow(floor = 17, ceiling = 19))

        assertIs<JdkSelectionResult.NoCompatibleJdk>(result)
    }

    @Test
    fun `selection is deterministic regardless of candidate order`() {
        val candidates =
            listOf(
                candidate("/jdk17a", 17, JdkSource.SYSTEM_PATH),
                candidate("/jdk21", 21, JdkSource.SDKMAN),
                candidate("/jdk8", 8, JdkSource.ASDF),
            )
        val window = JdkWindow(floor = null, ceiling = 23)

        val first = JdkSelector.select(candidates, window)
        val shuffled = JdkSelector.select(candidates.reversed(), window)

        assertEquals(first, shuffled)
    }

    @Test
    fun `equal versions from different sources break ties deterministically`() {
        val a = listOf(candidate("/a", 17, JdkSource.SDKMAN), candidate("/b", 17, JdkSource.GRADLE_JDKS))
        val b = listOf(candidate("/b", 17, JdkSource.GRADLE_JDKS), candidate("/a", 17, JdkSource.SDKMAN))

        val resultA = JdkSelector.select(a, JdkWindow(null, null))
        val resultB = JdkSelector.select(b, JdkWindow(null, null))

        assertIs<JdkSelectionResult.Selected>(resultA)
        assertEquals(resultA, resultB)
        assertEquals(JdkSource.GRADLE_JDKS, resultA.source)
    }
}
