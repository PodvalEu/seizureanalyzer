package seizureanalyzer.model

data class ParsedDrugLine(val name: String, val dosage: DrugDosage)

data class DrugParseResult(
    val matches: List<ParsedDrugLine>,
    val unmatchedSegments: List<String>,
)
