package tools.kaiju.gradlezilla.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GradlezillaHomeTest {
    private val home = File("/home/.gradlezilla/gradle-home")

    @Test
    fun projectCacheDir_isStableForTheSameProject() {
        val projectDir = File(".")

        val first = GradlezillaHome.projectCacheDir(home, projectDir)
        val second = GradlezillaHome.projectCacheDir(home, projectDir)

        assertEquals(first, second)
    }

    @Test
    fun projectCacheDir_differsForDifferentProjects() {
        val a = GradlezillaHome.projectCacheDir(home, File("."))
        val b = GradlezillaHome.projectCacheDir(home, File(".."))

        assertNotEquals(a, b)
    }

    @Test
    fun projectCacheDir_isNestedUnderProjectCachesInTheGivenHome() {
        val dir = GradlezillaHome.projectCacheDir(home, File("."))

        assertTrue(dir.absolutePath.startsWith(File(home, "project-caches").absolutePath))
    }
}
