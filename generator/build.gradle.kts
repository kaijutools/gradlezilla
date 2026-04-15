plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
