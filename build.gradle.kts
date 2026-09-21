import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    repositories {
        google()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JVM_17)
            }
            jvmToolchain(17)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId,
    )
    apply(
        plugin =
            rootProject.libs.plugins.detekt
                .get()
                .pluginId,
    )

    // Tell Kotlin explicitly what type of extension to configure
    extensions.configure<DetektExtension> {
        config.setFrom(files("$rootDir/detekt-config.yaml"))
        buildUponDefaultConfig = true
    }
}
