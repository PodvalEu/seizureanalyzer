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
