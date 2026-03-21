package seizureanalyzer

import com.google.api.client.json.gson.GsonFactory
import kotlinx.datetime.LocalDate

internal val JSON_FACTORY = GsonFactory.getDefaultInstance()
internal val ANALYSIS_START = LocalDate(2024, 1, 1)
internal val ANALYSIS_END = LocalDate(2026, 1, 15)
internal val ROLLING_WINDOWS = listOf(7, 14, 30)
internal val DRUG_COLOR_IDS = setOf("2")
internal const val BIG_SEIZURE_COLOR_ID = "3"
