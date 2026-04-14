plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("tools.kaiju.gradlezilla.cli.MainKt")
}

dependencies {
    implementation(libs.clikt)
}