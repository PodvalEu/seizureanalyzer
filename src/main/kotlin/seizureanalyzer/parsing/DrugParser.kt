package seizureanalyzer.parsing

import seizureanalyzer.model.DrugDosage
import seizureanalyzer.model.DrugParseResult
import seizureanalyzer.model.ParsedDrugLine

private val WHITESPACE_RUN = Regex("\\s+")

internal fun parseDrugSummary(summary: String): DrugParseResult {
    val normalized = normalizeDrugSummary(summary)

    val separators = listOf(" and ", "+", ",", ";")
    val separator = separators.firstOrNull { normalized.contains(it, ignoreCase = it == " and ") }
    val segments = if (separator != null) normalized.split(separator) else listOf(normalized)

    val matches = mutableListOf<ParsedDrugLine>()
    val unmatched = mutableListOf<String>()

    segments.map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { segment ->
            val match = DRUG_ENTRY_PATTERN.find(segment)
            if (match == null) {
                unmatched += segment.replace(WHITESPACE_RUN, " ")
            } else {
                val drugName = normalizeDrugName(match.groupValues[1])
                val morning = match.groupValues[2].toDoubleValue()
                val noon = match.groupValues[3].toDoubleValue()
                val evening = match.groupValues[4].toDoubleValue()
                matches += ParsedDrugLine(drugName, DrugDosage(morning, noon, evening))
            }
        }

    return DrugParseResult(matches, unmatched)
}
