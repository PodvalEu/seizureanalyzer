package seizureanalyzer.analysis

import kotlinx.datetime.LocalDate
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.DrugChangeImpact

internal fun analyzeDrugImpacts(
    rows: List<DailyRow>,
    categorized: CategorizedEvents,
    windowDays: Int = 14,
): List<DrugChangeImpact> {
    if (rows.isEmpty()) return emptyList()

    val dateIndex = rows.withIndex().associate { (i, r) -> r.date to i }
    val allChangeDates = categorized.drugChanges.values.flatten().map { it.date }.toSet()

    return categorized.drugChanges.flatMap { (drug, changes) ->
        changes.mapIndexedNotNull { changeIdx, change ->
            val idx = dateIndex[change.date] ?: return@mapIndexedNotNull null

            // Before window: [idx - windowDays, idx)
            val beforeStart = maxOf(0, idx - windowDays)
            val beforeDays = idx - beforeStart
            val beforeSeizures = if (beforeDays > 0) {
                (beforeStart until idx).sumOf { rows[it].smallSeizures + rows[it].bigSeizures }
            } else 0

            // After window: [idx, idx + windowDays)
            val afterEnd = minOf(rows.size, idx + windowDays)
            val afterDays = afterEnd - idx
            val afterSeizures = if (afterDays > 0) {
                (idx until afterEnd).sumOf { rows[it].smallSeizures + rows[it].bigSeizures }
            } else 0

            val avgBefore = if (beforeDays > 0) beforeSeizures.toDouble() / beforeDays else 0.0
            val avgAfter = if (afterDays > 0) afterSeizures.toDouble() / afterDays else 0.0

            val changePercent = if (avgBefore > 0) ((avgAfter - avgBefore) / avgBefore) * 100 else null

            // Previous dosage for this drug
            val dosageBefore = if (changeIdx > 0) changes[changeIdx - 1].dosage else null

            // Confounded: another drug also changed within the window
            val confounded = allChangeDates.any { otherDate ->
                otherDate != change.date &&
                    otherDate >= change.date.minusDays(windowDays) &&
                    otherDate <= change.date.plusDays(windowDays)
            }

            DrugChangeImpact(
                drug = drug,
                date = change.date,
                dosageBefore = dosageBefore,
                dosageAfter = change.dosage,
                windowDays = windowDays,
                avgDailySeizuresBefore = avgBefore,
                avgDailySeizuresAfter = avgAfter,
                changePercent = changePercent,
                confounded = confounded,
            )
        }
    }.sortedBy { it.date }
}

private fun LocalDate.minusDays(days: Int): LocalDate {
    val epoch = this.toEpochDays() - days
    return LocalDate.fromEpochDays(epoch)
}

private fun LocalDate.plusDays(days: Int): LocalDate {
    val epoch = this.toEpochDays() + days
    return LocalDate.fromEpochDays(epoch)
}
