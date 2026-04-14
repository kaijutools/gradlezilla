plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(libs.gradle.tooling)
    implementation(libs.sl4j)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
