package tools.kaiju.gradlezilla.models

import java.io.File
import java.util.Properties

object DaemonJvmCriteria {
    /** Reads the `toolchainVersion` pin from gradle/gradle-daemon-jvm.properties, if present. */
    fun read(projectDir: File): Int? {
        val props =
            File(projectDir, "gradle/gradle-daemon-jvm.properties")
                .takeIf { it.isFile } ?: return null

        return props
            .inputStream()
            .use { Properties().apply { load(it) } }
            .getProperty("toolchainVersion")
            ?.toIntOrNull()
    }
}
