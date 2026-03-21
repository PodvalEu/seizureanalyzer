package seizureanalyzer.analysis

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.DrugDosage

internal fun buildDailyRows(categorized: CategorizedEvents, start: LocalDate, end: LocalDate): List<DailyRow> {
    val days = generateDateRange(start, end)
    val drugs = categorized.detectedDrugs.sorted()
    val currentDosages: MutableMap<String, DrugDosage?> = drugs.associateWith { null }.toMutableMap()
    val changeQueues = categorized.drugChanges.mapValues { (_, changes) -> changes.toMutableList() }

    return days.map { date ->
        drugs.forEach { drug ->
            val queue = changeQueues[drug]
            while (!queue.isNullOrEmpty() && queue.first().date <= date) {
                currentDosages[drug] = queue.removeFirst().dosage
            }
        }

        DailyRow(
            date = date,
            drugDosages = drugs.associateWith { currentDosages[it] },
            smallSeizures = categorized.smallSeizuresByDate[date] ?: 0,
            bigSeizures = categorized.bigSeizuresByDate[date] ?: 0,
        )
    }
}

internal fun generateDateRange(start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
    val results = mutableListOf<LocalDate>()
    var current = start
    while (current <= endInclusive) {
        results += current
        current = current.plus(1, DateTimeUnit.DAY)
    }
    return results
}
