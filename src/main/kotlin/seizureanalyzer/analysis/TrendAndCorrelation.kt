package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import seizureanalyzer.model.DrugCorrelation
import seizureanalyzer.model.MonthlyStats
import kotlin.math.sqrt

internal fun computeMonthlyTrend(rows: List<DailyRow>): List<MonthlyStats> {
    return rows.groupBy { row ->
        val d = row.date
        "${d.year}-${d.monthNumber.toString().padStart(2, '0')}"
    }.map { (yearMonth, monthRows) ->
        val totalSmall = monthRows.sumOf { it.smallSeizures }
        val totalBig = monthRows.sumOf { it.bigSeizures }
        val days = monthRows.size
        MonthlyStats(
            yearMonth = yearMonth,
            totalSmall = totalSmall,
            totalBig = totalBig,
            daysWithData = days,
            avgDailyTotal = (totalSmall + totalBig).toDouble() / days,
            seizureFreeDays = monthRows.count { it.smallSeizures + it.bigSeizures == 0 },
            bigSeizureFreeDays = monthRows.count { it.bigSeizures == 0 },
        )
    }.sortedBy { it.yearMonth }
}

internal fun computeDrugCorrelations(rows: List<DailyRow>): List<DrugCorrelation> {
    if (rows.isEmpty()) return emptyList()

    val allDrugs = rows.flatMap { it.drugDosages.keys }.toSet()

    return allDrugs.mapNotNull { drug ->
        val onDrug = rows.filter { (it.drugDosages[drug]?.total() ?: 0.0) > 0 }
        val offDrug = rows.filter { (it.drugDosages[drug]?.total() ?: 0.0) == 0.0 || it.drugDosages[drug] == null }

        if (onDrug.isEmpty() || offDrug.isEmpty()) return@mapNotNull null

        val avgOn = onDrug.map { (it.smallSeizures + it.bigSeizures).toDouble() }.average()
        val avgOff = offDrug.map { (it.smallSeizures + it.bigSeizures).toDouble() }.average()

        // Pearson r between daily dose and daily seizure count (using 7d smoothed seizure counts)
        val paired = rows.mapNotNull paired@{ row ->
            val dose = row.drugDosages[drug]?.total() ?: return@paired null
            val seizures = row.getForwardSmall(7) + row.getForwardBig(7)
            dose to seizures.toDouble()
        }

        val r = if (paired.size > 2) pearsonR(paired) else 0.0

        DrugCorrelation(
            drug = drug,
            pearsonR = r,
            daysOnDrug = onDrug.size,
            daysOffDrug = offDrug.size,
            avgSeizuresOnDrug = avgOn,
            avgSeizuresOffDrug = avgOff,
        )
    }.sortedBy { it.pearsonR }
}

internal fun pearsonR(pairs: List<Pair<Double, Double>>): Double {
    val n = pairs.size
    val sumX = pairs.sumOf { it.first }
    val sumY = pairs.sumOf { it.second }
    val sumXY = pairs.sumOf { it.first * it.second }
    val sumX2 = pairs.sumOf { it.first * it.first }
    val sumY2 = pairs.sumOf { it.second * it.second }

    val numerator = n * sumXY - sumX * sumY
    val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))

    return if (denominator == 0.0) 0.0 else numerator / denominator
}
