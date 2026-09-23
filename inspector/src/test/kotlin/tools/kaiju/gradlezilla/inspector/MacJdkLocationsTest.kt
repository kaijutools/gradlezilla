package tools.kaiju.gradlezilla.inspector

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MacJdkLocationsTest {
    @Test
    fun `parses multiple JVMs from java_home -V output, excluding the trailing default line`() {
        val output =
            """
            Matching Java Virtual Machines (3):
                21.0.1 (arm64) "Eclipse Adoptium" - "OpenJDK 21.0.1" /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
                17.0.9 (arm64) "Eclipse Adoptium" - "OpenJDK 17.0.9" /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
                1.8.0_392 (x86_64) "Oracle Corporation" - "Java SE 8" /Library/Java/JavaVirtualMachines/jdk1.8.0_392.jdk/Contents/Home
            /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
            """.trimIndent()

        val homes = MacJdkLocations.parse(output)

        assertEquals(
            listOf(
                File("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"),
                File("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"),
                File("/Library/Java/JavaVirtualMachines/jdk1.8.0_392.jdk/Contents/Home"),
            ),
            homes,
        )
    }

    @Test
    fun `no matching JVMs yields an empty list`() {
        val output = "Unable to find any JVMs matching version \"99\"."

        assertEquals(emptyList(), MacJdkLocations.parse(output))
    }

    @Test
    fun `blank output yields an empty list`() {
        assertEquals(emptyList(), MacJdkLocations.parse(""))
    }
}
