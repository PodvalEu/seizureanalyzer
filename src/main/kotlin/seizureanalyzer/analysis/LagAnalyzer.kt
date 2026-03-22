package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.LagCorrelation

private val LAG_OFFSETS = listOf(0, 3, 7, 14, 21)

// Per-drug steady-state windows based on pharmacokinetics
private val DRUG_WINDOWS = mapOf(
    "Orfiril" to 5,
    "Topamax" to 5,
    "Lamictal" to 7,
    "Ontozry" to 14,
    "Zonegran" to 14,
    "Fycompa" to 21,
)
private const val DEFAULT_WINDOW = 14

internal fun computeLagCorrelations(rows: List<DailyRow>): List<LagCorrelation> {
    if (rows.isEmpty()) return emptyList()

    // Pre-compute prefix sums for seizure counts (small + big)
    val prefixSmall = IntArray(rows.size + 1)
    val prefixBig = IntArray(rows.size + 1)
    for (i in rows.indices) {
        prefixSmall[i + 1] = prefixSmall[i] + rows[i].smallSeizures
        prefixBig[i + 1] = prefixBig[i] + rows[i].bigSeizures
    }

    fun seizureSum(from: Int, window: Int): Int {
        val to = minOf(from + window, rows.size)
        return (prefixSmall[to] - prefixSmall[from]) + (prefixBig[to] - prefixBig[from])
    }

    val allDrugs = rows.flatMap { it.drugDosages.keys }.toSet()

    return allDrugs.flatMap { drug ->
        val window = DRUG_WINDOWS[drug] ?: DEFAULT_WINDOW

        LAG_OFFSETS.mapNotNull { lag ->
            val pairs = mutableListOf<Pair<Double, Double>>()

            for (i in rows.indices) {
                val dose = rows[i].drugDosages[drug]?.total() ?: continue
                val laggedIndex = i + lag
                if (laggedIndex >= rows.size) break
                pairs.add(dose to seizureSum(laggedIndex, window).toDouble())
            }

            if (pairs.size < 3) return@mapNotNull null

            LagCorrelation(
                drug = drug,
                lagDays = lag,
                pearsonR = pearsonR(pairs),
                sampleSize = pairs.size,
                windowDays = window,
            )
        }
    }.sortedWith(compareBy({ it.drug }, { it.lagDays }))
}
