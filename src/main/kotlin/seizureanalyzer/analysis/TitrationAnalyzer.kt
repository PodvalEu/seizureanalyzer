package seizureanalyzer.analysis

import seizureanalyzer.model.*

internal fun analyzeTitrations(
    rows: List<DailyRow>,
    categorized: CategorizedEvents,
    stabilityWindow: Int = 14,
): List<TitrationTrajectory> {
    if (rows.size < 3) return emptyList()

    val rowsByDate = rows.associateBy { it.date }
    val trajectories = mutableListOf<TitrationTrajectory>()

    for ((drug, changes) in categorized.drugChanges) {
        if (changes.size < 2) continue

        // Build dose-change pairs: consecutive changes in the same direction
        val steps = mutableListOf<TitrationStep>()
        var currentDirection: TitrationDirection? = null

        for (i in 1 until changes.size) {
            val prev = changes[i - 1]
            val curr = changes[i]
            val prevTotal = prev.dosage.total()
            val currTotal = curr.dosage.total()
            if (currTotal == prevTotal) {
                // No dose change — flush any pending sequence
                if (steps.size >= 2) {
                    trajectories.add(buildTrajectory(drug, currentDirection!!, steps, rows, rowsByDate, stabilityWindow))
                }
                steps.clear()
                currentDirection = null
                continue
            }

            val dir = if (currTotal > prevTotal) TitrationDirection.UP else TitrationDirection.DOWN

            if (currentDirection == null || dir == currentDirection) {
                // Continue or start a sequence
                if (steps.isEmpty()) {
                    steps.add(TitrationStep(prev.date, prevTotal, prevTotal))
                }
                steps.add(TitrationStep(curr.date, prevTotal, currTotal))
                currentDirection = dir
            } else {
                // Direction changed — flush previous sequence
                if (steps.size >= 2) {
                    trajectories.add(buildTrajectory(drug, currentDirection, steps, rows, rowsByDate, stabilityWindow))
                }
                steps.clear()
                steps.add(TitrationStep(prev.date, prevTotal, prevTotal))
                steps.add(TitrationStep(curr.date, prevTotal, currTotal))
                currentDirection = dir
            }
        }

        // Flush trailing sequence
        if (steps.size >= 2 && currentDirection != null) {
            trajectories.add(buildTrajectory(drug, currentDirection, steps, rows, rowsByDate, stabilityWindow))
        }
    }

    return trajectories.sortedWith(compareBy({ it.drug }, { it.startDate }))
}

private fun buildTrajectory(
    drug: String,
    direction: TitrationDirection,
    steps: List<TitrationStep>,
    rows: List<DailyRow>,
    rowsByDate: Map<kotlinx.datetime.LocalDate, DailyRow>,
    stabilityWindow: Int,
): TitrationTrajectory {
    val startDate = steps.first().date
    val endDate = steps.last().date

    // Pace: average days between consecutive dose steps
    val gaps = (1 until steps.size).map { i ->
        steps[i].date.toEpochDays() - steps[i - 1].date.toEpochDays()
    }
    val avgGap = if (gaps.isNotEmpty()) gaps.average() else 0.0
    val pace = when {
        avgGap < 7 -> PaceCategory.FAST
        avgGap <= 14 -> PaceCategory.NORMAL
        else -> PaceCategory.SLOW
    }

    // Seizure trend during titration: linear regression slope on daily seizure counts
    val duringRows = rows.filter { it.date >= startDate && it.date <= endDate }
    val seizureSlopeDuring = if (duringRows.size >= 2) {
        val pairs = duringRows.mapIndexed { idx, row ->
            idx.toDouble() to (row.smallSeizures + row.bigSeizures).toDouble()
        }
        linearSlope(pairs)
    } else 0.0

    val avgSeizuresDuring = if (duringRows.isNotEmpty()) {
        duringRows.sumOf { it.smallSeizures + it.bigSeizures }.toDouble() / duringRows.size
    } else 0.0

    // Stability after: avg seizures in the stabilityWindow days after titration ended
    val afterStartEpoch = endDate.toEpochDays() + 1
    val afterEndEpoch = endDate.toEpochDays() + stabilityWindow
    val afterStart = kotlinx.datetime.LocalDate.fromEpochDays(afterStartEpoch)
    val afterEnd = kotlinx.datetime.LocalDate.fromEpochDays(afterEndEpoch)
    val afterRows = rows.filter { it.date >= afterStart && it.date <= afterEnd }
    val avgSeizuresAfter = if (afterRows.isNotEmpty()) {
        afterRows.sumOf { it.smallSeizures + it.bigSeizures }.toDouble() / afterRows.size
    } else avgSeizuresDuring // fallback if no data after

    return TitrationTrajectory(
        drug = drug,
        direction = direction,
        steps = steps,
        startDate = startDate,
        endDate = endDate,
        paceCategory = pace,
        avgDaysBetweenSteps = avgGap,
        seizureSlopeDuring = seizureSlopeDuring,
        avgSeizuresDuring = avgSeizuresDuring,
        avgSeizuresAfter = avgSeizuresAfter,
    )
}

/** Simple linear regression slope: y = a + b*x, returns b. */
private fun linearSlope(pairs: List<Pair<Double, Double>>): Double {
    val n = pairs.size
    if (n < 2) return 0.0
    val sumX = pairs.sumOf { it.first }
    val sumY = pairs.sumOf { it.second }
    val sumXY = pairs.sumOf { it.first * it.second }
    val sumX2 = pairs.sumOf { it.first * it.first }
    val denom = n * sumX2 - sumX * sumX
    return if (denom == 0.0) 0.0 else (n * sumXY - sumX * sumY) / denom
}
