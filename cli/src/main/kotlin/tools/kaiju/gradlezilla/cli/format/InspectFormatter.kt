package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tools.kaiju.gradlezilla.cli.BuildConfig
import tools.kaiju.gradlezilla.inspector.BuildTarget

sealed class InspectFormatter {
    abstract fun format(targets: List<BuildTarget>): String

    object Human : InspectFormatter() {
        override fun format(targets: List<BuildTarget>): String {
            if (targets.isEmpty()) return ""
            return buildString {
                var currentGroup: String? = null
                for (target in targets) {
                    val group = target.group ?: "(ungrouped)"
                    if (group != currentGroup) {
                        if (currentGroup != null) appendLine()
                        appendLine(group)
                        appendLine("-".repeat(group.length))
                        currentGroup = group
                    }
                    val desc = if (target.description != null) " - ${target.description}" else ""
                    appendLine("  ${target.path}$desc")
                }
            }.trimEnd()
        }
    }

    object JsonOutput : InspectFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(targets: List<BuildTarget>): String = json.encodeToString(targets)
    }

    object SarifOutput : InspectFormatter() {
        private val json = Json { prettyPrint = true }

        override fun format(targets: List<BuildTarget>): String {
            val sarifLog = buildJsonObject {
                put("\$schema", "https://json.schemastore.org/sarif-2.1.0")
                put("version", "2.1.0")
                put("runs", buildJsonArray {
                    addJsonObject {
                        put("tool", buildJsonObject {
                            put("driver", buildJsonObject {
                                put("name", "gradlezilla")
                                put("version", BuildConfig.VERSION)
                            })
                        })
                        put("results", buildJsonArray {
                            for (target in targets) {
                                addJsonObject {
                                    put("ruleId", "build-target")
                                    put("kind", "informational")
                                    put("message", buildJsonObject {
                                        val desc = if (target.description != null) " - ${target.description}" else ""
                                        put("text", "${target.path}$desc")
                                    })
                                    put("properties", buildJsonObject {
                                        put("name", target.name)
                                        put("path", target.path)
                                        put("group", target.group)
                                    })
                                }
                            }
                        })
                    }
                })
            }
            return json.encodeToString(sarifLog)
        }
    }

    companion object {
        fun forFormat(format: String): InspectFormatter = when (format) {
            "json" -> JsonOutput
            "sarif" -> SarifOutput
            else -> Human
        }
    }
}
