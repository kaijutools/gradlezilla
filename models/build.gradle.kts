plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.gradle.tooling)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
