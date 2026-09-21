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
import java.io.PrintStream

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
        dir.mkdirs()

        // No org.gradle.jvmargs here: user-home properties override the project's, which
        // would drop repos' metaspace/GC flags and blow past the CI worker's memory budget.
        File(dir, PROPERTIES_FILE_NAME).writeText(
            "org.gradle.daemon.idletimeout=$IDLE_TIMEOUT_MS\n",
        )
        File(dir, MARKER_FILE_NAME).takeIf { !it.isFile }?.writeText("")

        if (isFirstRun) {
            System.err.println(
                "gradlezilla: warming Gradle cache at $dir — this run will be slower than usual",
            )
        }

        return Prepared.Ready(dir, isFirstRun)
    }

    private fun resolveDir(): File =
        System.getenv("GRADLEZILLA_GRADLE_HOME")?.let(::File)
            ?: File(System.getProperty("user.home"), ".gradlezilla/gradle-home")

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
                    PinnedConnectionResult.Success(block(RealPinnedConnection(connection, javaHome, prepared.dir)))
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
) : PinnedConnection {
    override fun <T> model(type: Class<T>): ModelBuilder<T> = connection.model(type).pinned()

    override fun <T> getModel(type: Class<T>): T = model(type).get()

    override fun <T> action(action: BuildAction<T>): BuildActionExecuter<T> = connection.action(action).pinned()

    override fun build(): BuildLauncher = connection.newBuild().pinned()

    private fun <L : LongRunningOperation> L.pinned(): L =
        apply {
            setJavaHome(javaHome)
            setStandardOutput(System.err)
            setStandardError(System.err)
        }
}
