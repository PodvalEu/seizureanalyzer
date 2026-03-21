package seizureanalyzer.parsing

import seizureanalyzer.model.DoseSlot
import seizureanalyzer.model.DrugDosage
import seizureanalyzer.model.DrugParseResult
import seizureanalyzer.model.ParsedDrugLine

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
                val singleDose = parseSingleDose(segment)
                if (singleDose != null) {
                    matches += singleDose
                } else {
                    unmatched += segment.replace(Regex("\\s+"), " ")
                }
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

private fun parseSingleDose(segment: String): ParsedDrugLine? {
    val pattern = Regex(
        "^\\s*(?:[\\p{So}\\p{Sk}]|\uD83D\uDC8A)?\\s*(.+?)\\s+(\\d+(?:[.,]\\d+)?)(?:\\s*(?:mg|ml|drops))?(.*)$",
        RegexOption.IGNORE_CASE,
    )
    val match = pattern.find(segment) ?: return null

    val drugName = normalizeDrugName(match.groupValues[1])
    val dose = match.groupValues[2].toDoubleValue()
    val context = match.groupValues[3]
    val slot = inferDoseSlot(segment + context + drugName)
    val dosage = when (slot) {
        DoseSlot.MORNING -> DrugDosage(dose, 0.0, 0.0)
        DoseSlot.NOON -> DrugDosage(0.0, dose, 0.0)
        DoseSlot.EVENING -> DrugDosage(0.0, 0.0, dose)
    }
    return ParsedDrugLine(drugName, dosage)
}

private fun inferDoseSlot(context: String): DoseSlot {
    val ctx = context.lowercase()
    return when {
        ctx.contains("evening") || ctx.contains("night") || ctx.contains("pm") || ctx.contains("večer") || ctx.contains("vecer") || ctx.contains("večerni") -> DoseSlot.EVENING
        ctx.contains("noon") || ctx.contains("midday") || ctx.contains("afternoon") || ctx.contains("odpoledne") -> DoseSlot.NOON
        ctx.contains("morning") || ctx.contains("am") || ctx.contains("rano") || ctx.contains("ráno") || ctx.contains("dopoledne") -> DoseSlot.MORNING
        ctx.contains("8pm") || ctx.contains("20:00") || ctx.contains("21:00") -> DoseSlot.EVENING
        ctx.contains("12pm") || ctx.contains("12:00") || ctx.contains("13:00") -> DoseSlot.NOON
        ctx.contains("8 pm") || ctx.contains("received") -> DoseSlot.EVENING
        else -> DoseSlot.MORNING
    }
}
