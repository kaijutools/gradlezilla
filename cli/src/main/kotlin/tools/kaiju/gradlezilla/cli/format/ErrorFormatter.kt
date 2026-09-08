package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

sealed class ErrorFormatter {
    abstract fun format(message: String): String

    object Human : ErrorFormatter() {
        override fun format(message: String): String = "Error: $message"
    }

    object JsonOutput : ErrorFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(message: String): String = json.encodeToString(ErrorOutput(error = message))
    }

    object SarifOutput : ErrorFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(message: String): String {
            val log =
                sarifLog {
                    put("results", buildJsonArray {})
                    put(
                        "invocations",
                        buildJsonArray {
                            addJsonObject {
                                put("executionSuccessful", false)
                                put(
                                    "toolExecutionNotifications",
                                    buildJsonArray {
                                        addJsonObject {
                                            put("level", "error")
                                            putJsonObject("message") { put("text", message) }
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            return json.encodeToString(log)
        }
    }

    companion object {
        fun forFormat(format: String): ErrorFormatter =
            when (format) {
                "json" -> JsonOutput
                "sarif" -> SarifOutput
                else -> Human
            }
    }
}

@Serializable
private data class ErrorOutput(
    val error: String,
)
