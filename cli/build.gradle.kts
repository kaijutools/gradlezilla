plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    application
}

application {
    mainClass.set("tools.kaiju.gradlezilla.cli.MainKt")
}

dependencies {
    implementation(libs.clikt)
}
