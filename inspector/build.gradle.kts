plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":models"))
    implementation(libs.ktoml.core)
    implementation(libs.ktoml.file)
    implementation(libs.gradle.tooling)

    implementation(libs.sl4j)

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
