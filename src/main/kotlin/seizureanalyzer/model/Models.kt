package seizureanalyzer.model

import kotlinx.datetime.LocalDate
import seizureanalyzer.parsing.formatNumber

data class DrugDosage(val morning: Double, val noon: Double, val evening: Double) {
    fun total(): Double = morning + noon + evening
    fun formatTriple(): String = listOf(morning, noon, evening).joinToString("-") { formatNumber(it) }
}

data class DrugChange(val date: LocalDate, val dosage: DrugDosage)

enum class TimeSource {
    EXPLICIT_TIME,
    CZECH_KEYWORD,
    LOCATION_INFERRED,
    UNKNOWN,
}

data class SeizureEvent(
    val date: LocalDate,
    val hour: Int?,
    val timeSource: TimeSource,
    val big: Boolean,
    val summary: String,
)

data class SkippedEvent(
    val date: LocalDate?,
    val summary: String,
    val colorId: String?,
    val reason: String,
)

data class CategorizedEvents(
    val drugChanges: Map<String, List<DrugChange>>,
    val smallSeizuresByDate: Map<LocalDate, Int>,
    val bigSeizuresByDate: Map<LocalDate, Int>,
    val detectedDrugs: Set<String>,
    val seizureEvents: List<SeizureEvent>,
    val oneTimeDrugs: Set<String> = emptySet(),
    val skippedEvents: List<SkippedEvent> = emptyList(),
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

enum class ChangeDirection { INCREASE, DECREASE }

data class ChangePoint(
    val date: LocalDate,
    val direction: ChangeDirection,
    val magnitude: Double,
    val cumulativeSum: Double,
    val activeDrugs: Map<String, Double>,
    val recentDrugChange: String?,
)

data class Burst(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Int,
    val totalSeizures: Int,
    val activeDrugs: Map<String, Double>,
)

data class VolatilityStats(
    val dosages: Map<String, Double>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Int,
    val avgDailySeizures: Double,
    val cv: Double,
    val dispersionIndex: Double,
    val bursts: List<Burst>,
)

enum class TitrationDirection { UP, DOWN }
enum class PaceCategory { FAST, NORMAL, SLOW }

data class TitrationStep(
    val date: LocalDate,
    val dosageBefore: Double,
    val dosageAfter: Double,
)

data class TitrationTrajectory(
    val drug: String,
    val direction: TitrationDirection,
    val steps: List<TitrationStep>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val paceCategory: PaceCategory,
    val avgDaysBetweenSteps: Double,
    val seizureSlopeDuring: Double,
    val avgSeizuresDuring: Double,
    val avgSeizuresAfter: Double,
)

data class AnalysisResults(
    val drugChangeImpacts: List<DrugChangeImpact>,
    val regimenRanking: List<RegimenStats>,
    val monthlyTrend: List<MonthlyStats>,
    val seizureFreeStreaks: List<SeizureFreeStreak>,
    val drugCorrelations: List<DrugCorrelation>,
    val lagCorrelations: List<LagCorrelation>,
    val changePoints: List<ChangePoint>,
    val volatilityAnalysis: List<VolatilityStats>,
    val titrationTrajectories: List<TitrationTrajectory>,
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
