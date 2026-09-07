package tools.kaiju.gradlezilla.inspector

import tools.kaiju.gradlezilla.models.AgpDataExtractor
import tools.kaiju.gradlezilla.models.ExtractionContext
import tools.kaiju.gradlezilla.models.ExtractionOutcome

class StaticBuildFileExtractor : AgpDataExtractor {
    override val name: String
        get() = StaticBuildFileExtractor::class.java.simpleName

    override fun extract(context: ExtractionContext): ExtractionOutcome = ExtractionOutcome.Failed("not implemented", null)
}
