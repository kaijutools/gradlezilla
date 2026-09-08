package tools.kaiju.gradlezilla.cli.format

import kotlinx.serialization.json.*
import tools.kaiju.gradlezilla.cli.BuildConfig

internal fun sarifLog(buildRun: JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject {
        put("\$schema", "https://json.schemastore.org/sarif-2.1.0")
        put("version", "2.1.0")
        put(
            "runs",
            buildJsonArray {
                addJsonObject {
                    putJsonObject("tool") {
                        putJsonObject("driver") {
                            put("name", "gradlezilla")
                            put("version", BuildConfig.VERSION)
                        }
                    }
                    buildRun()
                }
            },
        )
    }
