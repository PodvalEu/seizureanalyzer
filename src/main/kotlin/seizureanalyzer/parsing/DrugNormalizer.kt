package seizureanalyzer.parsing

import java.text.Normalizer

internal val DRUG_ENTRY_PATTERN = Regex(
    "^\\s*(?:[\\p{So}\\p{Sk}]|\uD83D\uDC8A)?\\s*(.+?)\\s+" +
        "(\\d+(?:[.,]\\d+)?)(?:\\s*(?:mg|ml|kapky|drops))?\\s*[-–]\\s*" +
        "(\\d+(?:[.,]\\d+)?)(?:\\s*(?:mg|ml|kapky|drops))?\\s*[-–]\\s*" +
        "(\\d+(?:[.,]\\d+)?)(?:\\s*(?:mg|ml|kapky|drops))?\\s*$",
    RegexOption.IGNORE_CASE,
)

internal val SINGLE_DOSE_PATTERN = Regex(
    "^\\s*(?:[\\p{So}\\p{Sk}]|\uD83D\uDC8A)?\\s*(.+?)\\s+" +
        "(\\d+(?:[.,]\\d+)?)\\s*(?:mg|ml|kapky|drops)(?:\\s.*)?$",
    RegexOption.IGNORE_CASE,
)

internal fun normalizeDrugSummary(summary: String): String = Normalizer.normalize(summary, Normalizer.Form.NFKC)
    .replace("–", "-")
    .replace(Regex("\\s+-\\s+"), " - ")
    .replace(Regex("\\s*mg\\b", RegexOption.IGNORE_CASE), " mg")
    .replace(Regex("\\s*ml\\b", RegexOption.IGNORE_CASE), " ml")
    .replace(Regex("\\s*kap(?:ka|ky)?\\b", RegexOption.IGNORE_CASE), " drops")

internal fun normalizeDrugName(rawName: String): String {
    val cleaned = Normalizer.normalize(rawName, Normalizer.Form.NFKD)
        .replace("\uD83D\uDC8A", "")
        .replace(Regex("^[^A-Za-z0-9]+"), "")
        .trim()
    val lower = cleaned.lowercase()
    return when {
        lower.contains("fycompa") -> "Fycompa"
        lower.contains("lamictal") -> "Lamictal"
        lower.contains("ontozry") -> "Ontozry"
        lower.contains("topamax") -> "Topamax"
        lower.contains("zonegran") || lower.contains("zonegram") || lower.contains("zonegraran") -> "Zonegran"
        lower.contains("diazepam") -> "Diazepam"
        lower.contains("magn") -> "Magnesium"
        lower.contains("vitamin d") -> "Vitamin D"
        lower.contains("viridikid") -> "Viridikid Multivitamin"
        lower.contains("viridian") -> "Viridikid Multivitamin"
        lower.contains("gs vápník") || lower.contains("vapnik") -> "Calcium Magnesium Zinc"
        lower.contains("olej z trescich jater") || lower.contains("cod liver") -> "Cod Liver Oil"
        else -> cleaned
    }
}
