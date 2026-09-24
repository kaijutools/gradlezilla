package tools.kaiju.gradlezilla.models

/**
 * One Android module's native-build configuration, read from the *evaluated* AGP extension
 * (so modules configured through convention plugins in `build-logic` are covered too).
 *
 * [usesCmake]/[usesNdkBuild] mean `externalNativeBuild.cmake.path` / `.ndkBuild.path` is set —
 * the only signal that AGP will actually *compile* native code. Prebuilt `.so` files under
 * `jniLibs`, `defaultConfig.ndk { abiFilters ... }`, and native libraries inside consumed AARs
 * deliberately do not count: AGP uses the NDK only to strip those, and merely warns when it is
 * absent. `android.ndkVersion` alone does not count either — AGP populates it with its own
 * bundled default whether or not the project builds native code, which is what made every
 * non-native project pull a ~1GB NDK into its image.
 */
data class ModuleNativeBuild(
    val path: String,
    val usesCmake: Boolean = false,
    val usesNdkBuild: Boolean = false,
    val ndkVersion: String? = null,
    val cmakeVersion: String? = null,
) {
    val buildsNativeCode: Boolean get() = usesCmake || usesNdkBuild
}

sealed interface NativeBuildOutcome {
    data class Found(
        val ndkVersion: String,
        val cmakeVersion: String? = null,
        val warnings: List<String> = emptyList(),
    ) : NativeBuildOutcome

    data class NotApplicable(
        val reason: String,
    ) : NativeBuildOutcome
}

object NativeBuildResolver {
    /**
     * AGP's default when a cmake-using module does not pin `externalNativeBuild.cmake.version`.
     *
     * Determined by disassembling AGP's own `CMakeVersion` enum (compiled from `CmakeLocator.kt`)
     * rather than from documentation: its static initializer assigns `DEFAULT = LATEST_WITH_FILE_API`,
     * whose `sdkFolderName` — the exact string `sdkmanager "cmake;<v>"` expects — is "3.22.1".
     * Identical in AGP 8.0.2 and 9.4.0, i.e. across the whole range this tool supports, so a
     * single constant suffices rather than a per-AGP table.
     *
     * This is only the fallback: `extractor.gradle` reads `CMakeVersion.DEFAULT` off AGP's own
     * classloader at extraction time when it can, so a future AGP that moves the default is
     * picked up without a code change here.
     */
    const val AGP_DEFAULT_CMAKE_VERSION = "3.22.1"

    fun resolve(
        modules: List<ModuleNativeBuild>,
        agpDefaultCmakeVersion: String? = null,
    ): NativeBuildOutcome {
        val native = modules.filter { it.buildsNativeCode }
        if (native.isEmpty()) return NativeBuildOutcome.NotApplicable("no externalNativeBuild configured")

        val pinned = native.filter { it.ndkVersion != null }
        if (pinned.isEmpty()) {
            return NativeBuildOutcome.NotApplicable(
                "externalNativeBuild configured in ${native.joinToString { it.path }} " +
                    "but AGP reported no ndkVersion",
            )
        }

        val warnings = mutableListOf<String>()
        val ndkVersion =
            pinned.mapNotNull { it.ndkVersion }.distinct().let { versions ->
                if (versions.size > 1) warnings += disagreement("ndkVersion", pinned) { it.ndkVersion }
                versions.maxWith(VERSION_ORDER)
            }

        val cmakeModules = native.filter { it.usesCmake }
        val cmakeVersion =
            if (cmakeModules.isEmpty()) {
                null
            } else {
                cmakeModules.mapNotNull { it.cmakeVersion }.distinct().let { versions ->
                    if (versions.size > 1) warnings += disagreement("cmake version", cmakeModules) { it.cmakeVersion }
                    versions.maxWithOrNull(VERSION_ORDER)
                        ?: agpDefaultCmakeVersion
                        ?: AGP_DEFAULT_CMAKE_VERSION
                }
            }

        return NativeBuildOutcome.Found(ndkVersion, cmakeVersion, warnings)
    }

    private fun disagreement(
        label: String,
        modules: List<ModuleNativeBuild>,
        select: (ModuleNativeBuild) -> String?,
    ): String =
        "Modules disagree on $label; using the highest. " +
            modules.filter { select(it) != null }.joinToString { "${it.path}=${select(it)}" }

    /** Numeric, component-wise: "29.0.14206865" outranks "9.0.99999999", which string order gets wrong. */
    private val VERSION_ORDER =
        Comparator<String> { a, b ->
            val left = a.split('.').map { it.toLongOrNull() ?: 0L }
            val right = b.split('.').map { it.toLongOrNull() ?: 0L }
            (0 until maxOf(left.size, right.size)).asSequence()
                .map { left.getOrElse(it) { 0L }.compareTo(right.getOrElse(it) { 0L }) }
                .firstOrNull { it != 0 } ?: 0
        }
}
