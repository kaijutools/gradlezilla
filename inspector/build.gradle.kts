plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(libs.gradle.tooling)
    implementation(libs.sl4j)
    implementation(libs.android.builder.model)
    implementation(project(":models"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
