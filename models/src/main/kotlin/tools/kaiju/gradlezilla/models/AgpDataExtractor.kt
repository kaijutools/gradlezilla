package tools.kaiju.gradlezilla.models

import java.io.File

interface AgpDataExtractor {
    @Throws(AgpDataExtractionException::class)
    fun extract(projectDir: File): AgpData?
}
