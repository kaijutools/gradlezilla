package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.json.*
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
            val log =
                sarifLog {
                    put(
                        "results",
                        buildJsonArray {
                            for (target in targets) {
                                addJsonObject {
                                    put("ruleId", "build-target")
                                    put("kind", "informational")
                                    put(
                                        "message",
                                        buildJsonObject {
                                            val desc =
                                                if (target.description != null) {
                                                    " - ${target.description}"
                                                } else {
                                                    ""
                                                }
                                            put("text", "${target.path}$desc")
                                        },
                                    )
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("name", target.name)
                                            put("path", target.path)
                                            put("group", target.group)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            return json.encodeToString(log)
        }
    }

    companion object {
        fun forFormat(format: String): InspectFormatter =
            when (format) {
                "json" -> JsonOutput
                "sarif" -> SarifOutput
                else -> Human
            }
    }
}
