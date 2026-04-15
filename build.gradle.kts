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