package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.SeizureFreeStreak

internal fun analyzeStreaks(rows: List<DailyRow>, minDays: Int = 3, topN: Int = 10): List<SeizureFreeStreak> {
    if (rows.isEmpty()) return emptyList()

    val allFree = findStreaks(rows, minDays, topN, bigOnly = false)
    val bigFree = findStreaks(rows, minDays, topN, bigOnly = true)

    return (allFree + bigFree).sortedByDescending { it.days }
}

private fun findStreaks(
    rows: List<DailyRow>,
    minDays: Int,
    topN: Int,
    bigOnly: Boolean,
): List<SeizureFreeStreak> {
    val streaks = mutableListOf<SeizureFreeStreak>()
    var streakStart = 0

    fun hasSeizure(row: DailyRow): Boolean =
        if (bigOnly) row.bigSeizures > 0 else (row.smallSeizures + row.bigSeizures) > 0

    for (i in rows.indices) {
        if (hasSeizure(rows[i])) {
            val length = i - streakStart
            if (length >= minDays) {
                streaks.add(buildStreak(rows, streakStart, i - 1, bigOnly))
            }
            streakStart = i + 1
        }
    }
    // Final streak
    val length = rows.size - streakStart
    if (length >= minDays) {
        streaks.add(buildStreak(rows, streakStart, rows.size - 1, bigOnly))
    }

    return streaks.sortedByDescending { it.days }.take(topN)
}

private fun buildStreak(rows: List<DailyRow>, start: Int, end: Int, bigOnly: Boolean): SeizureFreeStreak {
    val first = rows[start]
    val activeDrugs = first.drugDosages
        .filterValues { it != null }
        .mapValues { (_, dosage) -> dosage!!.total() }

    return SeizureFreeStreak(
        startDate = first.date,
        endDate = rows[end].date,
        days = end - start + 1,
        activeDrugs = activeDrugs,
        bigOnly = bigOnly,
    )
}
