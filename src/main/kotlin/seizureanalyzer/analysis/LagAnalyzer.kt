package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.LagCorrelation

private val LAG_OFFSETS = listOf(0, 3, 7, 14, 21)
private const val SUM_WINDOW = 7

internal fun computeLagCorrelations(rows: List<DailyRow>): List<LagCorrelation> {
    if (rows.isEmpty()) return emptyList()
    if (rows.first().forwardSums[SUM_WINDOW] == null) return emptyList()

    val allDrugs = rows.flatMap { it.drugDosages.keys }.toSet()

    return allDrugs.flatMap { drug ->
        LAG_OFFSETS.mapNotNull { lag ->
            val pairs = mutableListOf<Pair<Double, Double>>()

            for (i in rows.indices) {
                val dose = rows[i].drugDosages[drug]?.total() ?: continue
                val laggedIndex = i + lag
                if (laggedIndex >= rows.size) break
                val seizures = rows[laggedIndex].getForwardSmall(SUM_WINDOW) +
                    rows[laggedIndex].getForwardBig(SUM_WINDOW)
                pairs.add(dose to seizures.toDouble())
            }

            if (pairs.size < 3) return@mapNotNull null

            LagCorrelation(
                drug = drug,
                lagDays = lag,
                pearsonR = pearsonR(pairs),
                sampleSize = pairs.size,
            )
        }
    }.sortedWith(compareBy({ it.drug }, { it.lagDays }))
}
