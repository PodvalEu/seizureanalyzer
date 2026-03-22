package seizureanalyzer.analysis

import seizureanalyzer.model.Burst
import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.VolatilityStats
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun analyzeVolatility(rows: List<DailyRow>, minDays: Int = 7): List<VolatilityStats> {
    if (rows.isEmpty()) return emptyList()

    // Group consecutive days with the same regimen (same logic as RegimenAnalyzer)
    fun regimenKey(row: DailyRow): Map<String, Double> =
        row.drugDosages
            .filterValues { it != null }
            .mapValues { (_, dosage) -> roundDose(dosage!!.total()) }

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
        .map { segment -> computeVolatilityStats(segment, regimenKey(segment.first())) }
        .sortedBy { it.cv }
}

private fun computeVolatilityStats(segment: List<DailyRow>, dosages: Map<String, Double>): VolatilityStats {
    val dailyCounts = segment.map { (it.smallSeizures + it.bigSeizures).toDouble() }
    val n = dailyCounts.size
    val mean = dailyCounts.average()
    val variance = if (n > 1) dailyCounts.sumOf { (it - mean) * (it - mean) } / (n - 1) else 0.0
    val stddev = sqrt(variance)

    val cv = if (mean > 0) stddev / mean else 0.0
    val dispersionIndex = if (mean > 0) variance / mean else 0.0

    // Burst detection: consecutive days with seizures > mean + 1.5 * stddev
    val threshold = mean + 1.5 * stddev
    val bursts = detectBursts(segment, threshold)

    return VolatilityStats(
        dosages = dosages,
        startDate = segment.first().date,
        endDate = segment.last().date,
        days = n,
        avgDailySeizures = mean,
        cv = cv,
        dispersionIndex = dispersionIndex,
        bursts = bursts,
    )
}

private fun detectBursts(segment: List<DailyRow>, threshold: Double): List<Burst> {
    val bursts = mutableListOf<Burst>()
    var burstStart = -1

    for (i in segment.indices) {
        val total = segment[i].smallSeizures + segment[i].bigSeizures
        if (total > threshold) {
            if (burstStart == -1) burstStart = i
        } else {
            if (burstStart != -1) {
                bursts.add(buildBurst(segment, burstStart, i - 1))
                burstStart = -1
            }
        }
    }
    // Close trailing burst
    if (burstStart != -1) {
        bursts.add(buildBurst(segment, burstStart, segment.size - 1))
    }

    return bursts
}

private fun buildBurst(segment: List<DailyRow>, startIdx: Int, endIdx: Int): Burst {
    val burstRows = segment.subList(startIdx, endIdx + 1)
    val activeDrugs = burstRows.first().drugDosages
        .filterValues { it != null }
        .mapValues { (_, dosage) -> roundDose(dosage!!.total()) }

    return Burst(
        startDate = burstRows.first().date,
        endDate = burstRows.last().date,
        days = burstRows.size,
        totalSeizures = burstRows.sumOf { it.smallSeizures + it.bigSeizures },
        activeDrugs = activeDrugs,
    )
}

private fun roundDose(value: Double): Double =
    (value * 2).roundToInt() / 2.0
