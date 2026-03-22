package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.RegimenStats
import kotlin.math.roundToInt

internal fun analyzeRegimens(rows: List<DailyRow>, minDays: Int = 7): List<RegimenStats> {
    if (rows.isEmpty()) return emptyList()

    // Build regimen key for each row: drug -> rounded total dose
    fun regimenKey(row: DailyRow): Map<String, Double> =
        row.drugDosages
            .filterValues { it != null }
            .mapValues { (_, dosage) -> roundDose(dosage!!.total()) }

    // Group consecutive days with the same regimen
    val segments = mutableListOf<MutableList<DailyRow>>()
    var currentKey = regimenKey(rows.first())
    var currentSegment = mutableListOf(rows.first())

    for (i in 1 until rows.size) {
        val key = regimenKey(rows[i])
        if (key == currentKey) {
            currentSegment.add(rows[i])
        } else {
            segments.add(currentSegment)
            currentKey = key
            currentSegment = mutableListOf(rows[i])
        }
    }
    segments.add(currentSegment)

    return segments
        .filter { it.size >= minDays }
        .map { segment ->
            val days = segment.size
            val totalSmall = segment.sumOf { it.smallSeizures }
            val totalBig = segment.sumOf { it.bigSeizures }
            RegimenStats(
                dosages = regimenKey(segment.first()),
                days = days,
                totalSmall = totalSmall,
                totalBig = totalBig,
                avgDailySmall = totalSmall.toDouble() / days,
                avgDailyBig = totalBig.toDouble() / days,
                avgDailyTotal = (totalSmall + totalBig).toDouble() / days,
                startDate = segment.first().date,
                endDate = segment.last().date,
            )
        }
        .sortedBy { it.avgDailyTotal }
}

private fun roundDose(value: Double): Double =
    (value * 2).roundToInt() / 2.0
