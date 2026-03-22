package seizureanalyzer.analysis

import kotlinx.datetime.LocalDate
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.ChangeDirection
import seizureanalyzer.model.ChangePoint
import seizureanalyzer.model.DailyRow
import kotlin.math.abs

private const val DRUG_CHANGE_WINDOW_DAYS = 14
private const val THRESHOLD_FRACTION = 0.15 // 15% of total CUSUM range

internal fun detectChangePoints(
    rows: List<DailyRow>,
    categorized: CategorizedEvents,
): List<ChangePoint> {
    if (rows.size < 14) return emptyList()

    val dailyTotals = rows.map { (it.smallSeizures + it.bigSeizures).toDouble() }
    val mean = dailyTotals.average()
    val n = dailyTotals.size

    // Cumulative sum of residuals
    val cusum = DoubleArray(n)
    cusum[0] = dailyTotals[0] - mean
    for (i in 1 until n) {
        cusum[i] = cusum[i - 1] + (dailyTotals[i] - mean)
    }

    // Threshold: 15% of the total CUSUM range
    val cusumMin = cusum.min()
    val cusumMax = cusum.max()
    val threshold = (cusumMax - cusumMin) * THRESHOLD_FRACTION

    if (threshold <= 0.0) return emptyList()

    // Build flat list of all drug change dates for cross-referencing
    val allDrugChanges = categorized.drugChanges.flatMap { (drug, changes) ->
        changes.map { drug to it }
    }

    // Detect significant reversals using running max/min tracking.
    // When the CUSUM drops by >= threshold from the running max, that's a DECREASE (improvement).
    // When the CUSUM rises by >= threshold from the running min, that's an INCREASE (worsening).
    val result = mutableListOf<ChangePoint>()
    var runningMax = cusum[0]
    var runningMaxIdx = 0
    var runningMin = cusum[0]
    var runningMinIdx = 0
    var lastDetected = ""

    for (i in 1 until n) {
        if (cusum[i] > runningMax) {
            runningMax = cusum[i]
            runningMaxIdx = i
        }
        if (cusum[i] < runningMin) {
            runningMin = cusum[i]
            runningMinIdx = i
        }

        val dropFromMax = runningMax - cusum[i]
        val riseFromMin = cusum[i] - runningMin

        if (dropFromMax >= threshold && lastDetected != "drop") {
            // Significant drop detected — the change point is at the peak (where it started dropping)
            result.add(buildChangePoint(rows, cusum, runningMaxIdx, ChangeDirection.DECREASE, dropFromMax, allDrugChanges))
            lastDetected = "drop"
            // Reset tracking
            runningMin = cusum[i]
            runningMinIdx = i
            runningMax = cusum[i]
            runningMaxIdx = i
        } else if (riseFromMin >= threshold && lastDetected != "rise") {
            // Significant rise detected — the change point is at the trough (where it started rising)
            result.add(buildChangePoint(rows, cusum, runningMinIdx, ChangeDirection.INCREASE, riseFromMin, allDrugChanges))
            lastDetected = "rise"
            // Reset tracking
            runningMax = cusum[i]
            runningMaxIdx = i
            runningMin = cusum[i]
            runningMinIdx = i
        }
    }

    return result.sortedBy { it.date }
}

/** Returns the CUSUM curve as date→value pairs for chart rendering. */
internal fun computeCusumCurve(rows: List<DailyRow>): List<Pair<LocalDate, Double>> {
    if (rows.isEmpty()) return emptyList()

    val dailyTotals = rows.map { (it.smallSeizures + it.bigSeizures).toDouble() }
    val mean = dailyTotals.average()

    var cumSum = 0.0
    return rows.zip(dailyTotals).map { (row, total) ->
        cumSum += total - mean
        row.date to cumSum
    }
}

private fun buildChangePoint(
    rows: List<DailyRow>,
    cusum: DoubleArray,
    index: Int,
    direction: ChangeDirection,
    magnitude: Double,
    allDrugChanges: List<Pair<String, seizureanalyzer.model.DrugChange>>,
): ChangePoint {
    val row = rows[index]
    val activeDrugs = row.drugDosages
        .filterValues { it != null }
        .mapValues { (_, dosage) -> dosage!!.total() }

    return ChangePoint(
        date = row.date,
        direction = direction,
        magnitude = magnitude,
        cumulativeSum = cusum[index],
        activeDrugs = activeDrugs,
        recentDrugChange = findNearestDrugChange(row.date, allDrugChanges),
    )
}

private fun findNearestDrugChange(
    date: LocalDate,
    allDrugChanges: List<Pair<String, seizureanalyzer.model.DrugChange>>,
): String? {
    val dateEpoch = date.toEpochDays()
    var bestDrug: String? = null
    var bestDistance = Int.MAX_VALUE

    for ((drug, change) in allDrugChanges) {
        val distance = abs(change.date.toEpochDays() - dateEpoch)
        if (distance <= DRUG_CHANGE_WINDOW_DAYS && distance < bestDistance) {
            bestDistance = distance
            bestDrug = "$drug ${change.dosage.formatTriple()}"
        }
    }

    return bestDrug
}
