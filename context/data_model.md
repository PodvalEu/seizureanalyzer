# Data Model

## Core Data Models (model/Models.kt)

```kotlin
data class DrugDosage(val morning: Double, val noon: Double, val evening: Double) {
    fun total(): Double = morning + noon + evening
}
data class DrugChange(val date: LocalDate, val dosage: DrugDosage)
data class CategorizedEvents(
    val drugChanges: Map<String, List<DrugChange>>,
    val smallSeizuresByDate: Map<LocalDate, Int>,
    val bigSeizuresByDate: Map<LocalDate, Int>,
    val detectedDrugs: Set<String>,  // actually sortedSetOf internally
)
data class DailyRow(
    val date: LocalDate,
    val drugDosages: Map<String, DrugDosage?>,  // null = drug not yet started
    val smallSeizures: Int,
    val bigSeizures: Int,
    val forwardSums: MutableMap<Int, Pair<Int, Int>>,  // window -> (small, big)
)
```

## Drug Name Mappings (DrugNormalizer.kt)
Normalizes various spellings/Czech names to canonical names:
- fycompa -> Fycompa
- lamictal -> Lamictal
- ontozry -> Ontozry
- topamax -> Topamax
- zonegran/zonegram/zonegraran -> Zonegran
- diazepam/"received" -> Diazepam
- magn* -> Magnesium
- vitamin d -> Vitamin D
- viridikid/viridian -> Viridikid Multivitamin
- gs vapnik -> Calcium Magnesium Zinc
- olej z trescich jater/cod liver -> Cod Liver Oil

## Drug Dosage Parsing
- **Full format**: `💊 Drug morning-noon-evening` (e.g., "💊 Fycompa 0-0-6 mg")
  - Regex: `DRUG_ENTRY_PATTERN` handles optional emoji, drug name, three dose values
- **Single dose**: `Drug dose [context]` — infers slot (morning/noon/evening) from context keywords
  - Czech keywords supported: vecer, rano, odpoledne
- **Multi-drug**: Events with `+`, `,`, `;`, or `and` separators

## Drug Normalization for Chart
In HtmlReportWriter.kt, each drug's daily total dosage is normalized to 0-1 range:
- maxValue = max of all non-null daily totals for that drug
- normalized = value / maxValue

## Rolling Sum Algorithm (RollingSum.kt)
Uses prefix-sum arrays for O(n) computation:
- Forward-looking: from each day, sum the NEXT `window` days (7/14/30)
- This correlates drug change dates with subsequent seizure counts

## Chart Data Contract (JSON passed to HTML template)
| Variable | Type | Description |
|----------|------|-------------|
| `labelsJson` | `string[]` | Date strings for X axis ("2024-01-01", ...) |
| `seriesJson` | `EChartsSeries[]` | All drug + seizure series configs |
| `drugLegendJson` | `string[]` | Drug names in order |
| `seizureGroupsJson` | `object[]` | Window groups with label, smallName, bigName, colors, defaultVisible |
| `legendSelectedJson` | `object` | Map of series name -> false (for initially hidden series) |
| `totalSmall` | `int` | Total small seizures in period |
| `totalBig` | `int` | Total big seizures in period |

Plus `Config.analysisStart` and `Config.analysisEnd` interpolated directly.
