package seizureanalyzer.model

import kotlinx.datetime.LocalDate
import seizureanalyzer.parsing.formatNumber

data class DrugDosage(val morning: Double, val noon: Double, val evening: Double) {
    fun total(): Double = morning + noon + evening
    fun formatTriple(): String = listOf(morning, noon, evening).joinToString("-") { formatNumber(it) }
}

data class DrugChange(val date: LocalDate, val dosage: DrugDosage)

data class CategorizedEvents(
    val drugChanges: Map<String, List<DrugChange>>,
    val smallSeizuresByDate: Map<LocalDate, Int>,
    val bigSeizuresByDate: Map<LocalDate, Int>,
    val detectedDrugs: Set<String>,
)

// ── Analysis result models ──

data class DrugChangeImpact(
    val drug: String,
    val date: LocalDate,
    val dosageBefore: DrugDosage?,
    val dosageAfter: DrugDosage,
    val windowDays: Int,
    val avgDailySeizuresBefore: Double,
    val avgDailySeizuresAfter: Double,
    val changePercent: Double?,
    val confounded: Boolean,
)

data class RegimenStats(
    val dosages: Map<String, Double>,
    val days: Int,
    val totalSmall: Int,
    val totalBig: Int,
    val avgDailySmall: Double,
    val avgDailyBig: Double,
    val avgDailyTotal: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

data class MonthlyStats(
    val yearMonth: String,
    val totalSmall: Int,
    val totalBig: Int,
    val daysWithData: Int,
    val avgDailyTotal: Double,
    val seizureFreeDays: Int,
    val bigSeizureFreeDays: Int,
)

data class SeizureFreeStreak(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Int,
    val activeDrugs: Map<String, Double>,
    val bigOnly: Boolean,
)

data class DrugCorrelation(
    val drug: String,
    val pearsonR: Double,
    val daysOnDrug: Int,
    val daysOffDrug: Int,
    val avgSeizuresOnDrug: Double,
    val avgSeizuresOffDrug: Double,
)

data class LagCorrelation(
    val drug: String,
    val lagDays: Int,
    val pearsonR: Double,
    val sampleSize: Int,
    val windowDays: Int,
)

data class AnalysisResults(
    val drugChangeImpacts: List<DrugChangeImpact>,
    val regimenRanking: List<RegimenStats>,
    val monthlyTrend: List<MonthlyStats>,
    val seizureFreeStreaks: List<SeizureFreeStreak>,
    val drugCorrelations: List<DrugCorrelation>,
    val lagCorrelations: List<LagCorrelation>,
)

// ── Core data models ──

data class DailyRow(
    val date: LocalDate,
    val drugDosages: Map<String, DrugDosage?>,
    val smallSeizures: Int,
    val bigSeizures: Int,
    val forwardSums: MutableMap<Int, Pair<Int, Int>> = mutableMapOf(),
) {
    fun setForwardSum(window: Int, small: Int, big: Int) {
        forwardSums[window] = small to big
    }

    fun getForwardSmall(window: Int): Int = forwardSums[window]?.first ?: 0
    fun getForwardBig(window: Int): Int = forwardSums[window]?.second ?: 0
}
