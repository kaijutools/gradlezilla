package tools.kaiju.gradlezilla.models

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.security.MessageDigest

/**
 * An isolated, persistent Gradle user home so gradlezilla's daemon never shares a registry
 * (and gets nondeterministically matched against) daemons spawned by Android Studio or other
 * projects. Persistent rather than temp, so distributions aren't re-downloaded every run.
 */
object GradlezillaHome {
    private const val IDLE_TIMEOUT_MS = 60_000L
    private const val MARKER_FILE_NAME = ".gradlezilla-owned"
    private const val PROPERTIES_FILE_NAME = "gradle.properties"

    sealed class Prepared {
        data class Ready(
            val dir: File,
            val isFirstRun: Boolean,
        ) : Prepared()

        data class Rejected(
            val reason: String,
        ) : Prepared()
    }

    fun prepare(): Prepared {
        val dir = resolveDir()
        ownershipProblem(dir)?.let { return Prepared.Rejected(it) }

        val isFirstRun = !dir.exists()

        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return Prepared.Rejected("Could not create Gradle home directory '$dir'")
            }

            // Marker written before gradle.properties: a process interrupted between the two
            // writes leaves a marked-but-incomplete home that the next invocation can safely
            // finish, rather than an unmarked, non-empty home that gets rejected as foreign.
            File(dir, MARKER_FILE_NAME).takeIf { !it.isFile }?.writeText("")

            // No org.gradle.jvmargs here: user-home properties override the project's, which
            // would drop repos' metaspace/GC flags and blow past the CI worker's memory budget.
            File(dir, PROPERTIES_FILE_NAME).writeText(
                "org.gradle.daemon.idletimeout=$IDLE_TIMEOUT_MS\n",
            )

            if (isFirstRun) {
                System.err.println(
                    "gradlezilla: warming Gradle cache at $dir — this run will be slower than usual",
                )
            }

            Prepared.Ready(dir, isFirstRun)
        } catch (e: IOException) {
            Prepared.Rejected("Could not write Gradle home config in '$dir': ${e.message}")
        }
    }

    private fun resolveDir(): File =
        System.getenv("GRADLEZILLA_GRADLE_HOME")?.let(::File)
            ?: File(System.getProperty("user.home"), ".gradlezilla/gradle-home")

    /**
     * A persistent, per-project cache directory under [home] — Gradle's `--project-cache-dir`,
     * which is where configuration-cache entries, task-execution history, etc. actually live.
     * Left pointed at the target project's own directory (the default), every gradlezilla run
     * writes a `.gradle/configuration-cache` into someone else's repo — the same problem
     * [prepare] solves for the Gradle user home. Persistent rather than temp so repeat runs
     * against the same project stay fast; keyed by a hash of the canonical project path so
     * distinct projects never collide. Never cleaned up here — same lifetime policy as [prepare]'s
     * `dir`, left to whatever external housekeeping the user home itself gets.
     */
    fun projectCacheDir(
        home: File,
        projectDir: File,
    ): File {
        val canonicalPath = projectDir.canonicalFile.path
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPath.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }.take(HASH_PREFIX_LENGTH)
        return File(home, "project-caches/$hash")
    }

    private const val HASH_PREFIX_LENGTH = 16

    /** Non-null when [dir] has content we didn't put there — refuse to clobber it. */
    private fun ownershipProblem(dir: File): String? {
        if (!dir.exists()) return null
        if (File(dir, MARKER_FILE_NAME).isFile) return null
        if (dir.list()?.isEmpty() != false) return null
        return "Refusing to write Gradle settings into '$dir': it already has content and isn't " +
            "a Gradlezilla-managed home. Set GRADLEZILLA_GRADLE_HOME to a dedicated directory."
    }
}

sealed class PinnedConnectionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PinnedConnectionResult<T>()

    data class Rejected(
        val reason: String,
    ) : PinnedConnectionResult<Nothing>()
}

/**
 * A Gradle Tooling API connection pinned to a specific JDK, with stdout/stderr routed to
 * System.err so a caller's stdout stays reserved for its own output (e.g. `--format json`).
 * Only reachable via [withConnection] — there is no public way to obtain the raw
 * [ProjectConnection] this wraps.
 */
interface PinnedConnection {
    val gradleUserHome: File
    val projectCacheDir: File

    fun <T> model(type: Class<T>): ModelBuilder<T>

    fun <T> getModel(type: Class<T>): T

    fun <T> action(action: BuildAction<T>): BuildActionExecuter<T>

    fun build(): BuildLauncher

    companion object {
        fun <T> withConnection(
            projectDir: File,
            javaHome: File,
            block: (PinnedConnection) -> T,
        ): PinnedConnectionResult<T> {
            val prepared =
                when (val result = GradlezillaHome.prepare()) {
                    is GradlezillaHome.Prepared.Rejected -> return PinnedConnectionResult.Rejected(result.reason)
                    is GradlezillaHome.Prepared.Ready -> result
                }

            val projectCacheDir = GradlezillaHome.projectCacheDir(prepared.dir, projectDir)
            projectCacheDir.mkdirs()

            // Distribution-download progress (e.g. first-ever run against a Gradle version) is
            // written straight to System.out by the Tooling API's connector bootstrap, outside
            // of setStandardOutput's reach. Redirect process-wide for the connection's lifetime
            // so it can never land ahead of a caller's own stdout output.
            val realStdout = System.out
            System.setOut(PrintStream(FileOutputStream(FileDescriptor.err)))
            try {
                val connection =
                    GradleConnector
                        .newConnector()
                        .forProjectDirectory(projectDir)
                        .useGradleUserHomeDir(prepared.dir)
                        .connect()

                return try {
                    val pinned = RealPinnedConnection(connection, javaHome, prepared.dir, projectCacheDir)
                    PinnedConnectionResult.Success(block(pinned))
                } finally {
                    connection.close()
                }
            } finally {
                System.setOut(realStdout)
            }
        }
    }
}

private class RealPinnedConnection(
    private val connection: ProjectConnection,
    private val javaHome: File,
    override val gradleUserHome: File,
    override val projectCacheDir: File,
) : PinnedConnection {
    override fun <T> model(type: Class<T>): ModelBuilder<T> = connection.model(type).pinned()

    override fun <T> getModel(type: Class<T>): T = model(type).get()

    override fun <T> action(action: BuildAction<T>): BuildActionExecuter<T> = connection.action(action).pinned()

    override fun build(): BuildLauncher = connection.newBuild().pinned()

    /**
     * `withArguments` replaces rather than accumulates, so callers that need their own arguments
     * (e.g. InitScriptExtractor's init script + cache-bust property) must fold `--project-cache-dir`
     * into that same call themselves — [projectCacheDir] is exposed on [PinnedConnection] for
     * exactly that. Set here too so operations that never call `withArguments` (the plain model
     * fetches in `fetchEnvironment`) still get it.
     */
    private fun <L : LongRunningOperation> L.pinned(): L =
        apply {
            setJavaHome(javaHome)
            setStandardOutput(System.err)
            setStandardError(System.err)
            withArguments("--project-cache-dir", projectCacheDir.absolutePath)
        }
}
