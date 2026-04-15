package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.AgpData
import tools.kaiju.gradlezilla.models.AgpDataExtractor
import java.io.File

class StaticBuildFileExtractor : AgpDataExtractor {
    override fun extract(projectDir: File): AgpData? = null
}
