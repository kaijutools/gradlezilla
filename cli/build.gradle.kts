plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    application
}

version = "0.1.0"

val gitSha: Provider<String> =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText
        .map { it.trim() }

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/buildConfig/main/kotlin")
    val projectVersion = version.toString()

    inputs.property("version", projectVersion)
    inputs.property("commitSha", gitSha)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("tools/kaiju/gradlezilla/cli/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package tools.kaiju.gradlezilla.cli

            internal object BuildConfig {
                const val VERSION = "$projectVersion"
                const val COMMIT_SHA = "${gitSha.get()}"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildConfig.map { it.outputs.files })
    }
}

application {
    mainClass.set("tools.kaiju.gradlezilla.cli.MainKt")
    applicationName = "gradlezilla"
}

dependencies {
    implementation(libs.clikt)
    implementation(project(":models"))
    implementation(project(":inspector"))
    implementation(project(":generator"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
