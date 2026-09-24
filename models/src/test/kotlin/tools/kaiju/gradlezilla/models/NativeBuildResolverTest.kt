package tools.kaiju.gradlezilla.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeBuildResolverTest {
    @Test
    fun `no modules at all is NotApplicable`() {
        val outcome = assertIs<NativeBuildOutcome.NotApplicable>(NativeBuildResolver.resolve(emptyList()))
        assertEquals("no externalNativeBuild configured", outcome.reason)
    }

    @Test
    fun `a module reporting an ndkVersion but no externalNativeBuild is NotApplicable`() {
        // The timber / nowinandroid shape: AGP reports its bundled default ndkVersion on a
        // project with no native sources whatsoever.
        val outcome =
            assertIs<NativeBuildOutcome.NotApplicable>(
                NativeBuildResolver.resolve(listOf(ModuleNativeBuild(":app", ndkVersion = "25.1.8937393"))),
            )
        assertEquals("no externalNativeBuild configured", outcome.reason)
    }

    @Test
    fun `a cmake module yields both ndk and cmake`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(
                        ModuleNativeBuild(":native", usesCmake = true, ndkVersion = "26.1.1", cmakeVersion = "3.22.1"),
                    ),
                ),
            )
        assertEquals("26.1.1", outcome.ndkVersion)
        assertEquals("3.22.1", outcome.cmakeVersion)
        assertTrue(outcome.warnings.isEmpty())
    }

    @Test
    fun `an ndkBuild module yields ndk only`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(ModuleNativeBuild(":native", usesNdkBuild = true, ndkVersion = "26.1.1")),
                ),
            )
        assertEquals("26.1.1", outcome.ndkVersion)
        assertNull(outcome.cmakeVersion)
    }

    @Test
    fun `a cmake module with no pinned version falls back to AGP's default`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(ModuleNativeBuild(":native", usesCmake = true, ndkVersion = "26.1.1")),
                ),
            )
        assertEquals(NativeBuildResolver.AGP_DEFAULT_CMAKE_VERSION, outcome.cmakeVersion)
    }

    @Test
    fun `an AGP-reported default cmake version wins over the documented constant`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(ModuleNativeBuild(":native", usesCmake = true, ndkVersion = "26.1.1")),
                    agpDefaultCmakeVersion = "3.30.5",
                ),
            )
        assertEquals("3.30.5", outcome.cmakeVersion)
    }

    @Test
    fun `only native modules contribute their ndkVersion`() {
        // :app reports the higher version but builds nothing native, so it must not win.
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(
                        ModuleNativeBuild(":app", ndkVersion = "99.0.0"),
                        ModuleNativeBuild(":native", usesCmake = true, ndkVersion = "26.1.1"),
                    ),
                ),
            )
        assertEquals("26.1.1", outcome.ndkVersion)
        assertTrue(outcome.warnings.isEmpty())
    }

    @Test
    fun `disagreeing modules take the highest and warn with module paths and versions`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(
                        ModuleNativeBuild(":a", usesCmake = true, ndkVersion = "9.0.99999999"),
                        ModuleNativeBuild(":b", usesCmake = true, ndkVersion = "29.0.14206865"),
                    ),
                ),
            )
        // Numeric, not lexicographic: "29..." must beat "9..." despite sorting lower as a string.
        assertEquals("29.0.14206865", outcome.ndkVersion)
        assertEquals(1, outcome.warnings.size)
        assertTrue(outcome.warnings.single().contains(":a=9.0.99999999"))
        assertTrue(outcome.warnings.single().contains(":b=29.0.14206865"))
    }

    @Test
    fun `disagreeing cmake versions take the highest and warn`() {
        val outcome =
            assertIs<NativeBuildOutcome.Found>(
                NativeBuildResolver.resolve(
                    listOf(
                        ModuleNativeBuild(":a", usesCmake = true, ndkVersion = "26.1.1", cmakeVersion = "3.10.2"),
                        ModuleNativeBuild(":b", usesCmake = true, ndkVersion = "26.1.1", cmakeVersion = "3.22.1"),
                    ),
                ),
            )
        assertEquals("3.22.1", outcome.cmakeVersion)
        assertEquals(1, outcome.warnings.size)
        assertTrue(outcome.warnings.single().contains("cmake version"))
    }

    @Test
    fun `externalNativeBuild with no ndkVersion anywhere is NotApplicable and names the modules`() {
        val outcome =
            assertIs<NativeBuildOutcome.NotApplicable>(
                NativeBuildResolver.resolve(listOf(ModuleNativeBuild(":native", usesCmake = true))),
            )
        assertTrue(outcome.reason.contains(":native"))
        assertTrue(outcome.reason.contains("no ndkVersion"))
    }
}
