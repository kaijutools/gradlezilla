package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import tools.kaiju.gradlezilla.models.AndroidProjectSpec

sealed class GenerateFormatter {
    abstract fun format(
        spec: AndroidProjectSpec,
        dockerfile: String,
        outputPath: String?,
    ): String

    object Human : GenerateFormatter() {
        override fun format(
            spec: AndroidProjectSpec,
            dockerfile: String,
            outputPath: String?,
        ): String =
            buildString {
                appendLine("✅ Inspection complete:\n${spec.humanSummary()}\n")
                if (outputPath == null) {
                    appendLine("--- DRY RUN: Generated Dockerfile ---")
                    append(dockerfile)
                } else {
                    append("🚀 Successfully wrote Dockerfile to: $outputPath")
                }
            }
    }

    object JsonOutput : GenerateFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(
            spec: AndroidProjectSpec,
            dockerfile: String,
            outputPath: String?,
        ): String = json.encodeToString(GenerateOutput(spec = spec, dockerfile = dockerfile, outputPath = outputPath))
    }

    object SarifOutput : GenerateFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(
            spec: AndroidProjectSpec,
            dockerfile: String,
            outputPath: String?,
        ): String {
            val log =
                sarifLog {
                    put(
                        "artifacts",
                        buildJsonArray {
                            addJsonObject {
                                putJsonObject("location") { put("uri", "Dockerfile") }
                                putJsonObject("contents") { put("text", dockerfile) }
                            }
                        },
                    )
                    put("results", buildJsonArray {})
                    putJsonObject("properties") {
                        put("jdkVersion", spec.jdkVersion)
                        put("androidSdkVersion", spec.androidSdkVersion)
                        put("androidCommandLineToolsVersion", spec.androidCommandLineToolsVersion)
                        put("androidPlatformToolsVersion", spec.androidPlatformToolsVersion)
                        put("androidNdkVersion", spec.androidNdkVersion)
                        put("androidCmakeVersion", spec.androidCmakeVersion)
                        put("gradleVersion", spec.gradleVersion)
                        put("outputPath", outputPath)
                    }
                }
            return json.encodeToString(log)
        }
    }

    companion object {
        fun forFormat(format: String): GenerateFormatter =
            when (format) {
                "json" -> JsonOutput
                "sarif" -> SarifOutput
                else -> Human
            }
    }
}

private fun AndroidProjectSpec.humanSummary(): String =
    buildList {
        add("JDK: $jdkVersion")
        add("CLI tools: $androidCommandLineToolsVersion")
        add("Android sdk: $androidSdkVersion")
        add("Platform tools: $androidPlatformToolsVersion")
        add("Ndk: $androidNdkVersion")
    }.joinToString("\n")

@Serializable
private data class GenerateOutput(
    val spec: AndroidProjectSpec,
    val dockerfile: String,
    val outputPath: String?,
)
