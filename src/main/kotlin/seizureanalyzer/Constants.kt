package seizureanalyzer

import com.google.api.client.json.gson.GsonFactory
import kotlinx.datetime.LocalDate

internal val JSON_FACTORY = GsonFactory.getDefaultInstance()
internal val ANALYSIS_START = LocalDate(2024, 1, 1)
internal val ANALYSIS_END = LocalDate(2026, 1, 15)
internal val ROLLING_WINDOWS = listOf(7, 14, 30)
internal val DRUG_COLOR_IDS = setOf("2")

internal val COLOR_PALETTE = listOf(
    "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
    "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
)
