package seizureanalyzer

import io.github.cdimascio.dotenv.dotenv
import kotlinx.datetime.LocalDate

internal object Config {
    private val configEnv = dotenv {
        filename = "config.env"
        ignoreIfMissing = true
    }
    private val secretsEnv = dotenv {
        filename = ".env"
        ignoreIfMissing = true
    }

    private operator fun get(name: String): String? =
        System.getenv(name) ?: secretsEnv[name] ?: configEnv[name]

    val analysisStart: LocalDate = this["ANALYSIS_START"]?.let { LocalDate.parse(it) }
        ?: LocalDate(2024, 1, 1)

    val analysisEnd: LocalDate = this["ANALYSIS_END"]?.let { LocalDate.parse(it) }
        ?: LocalDate(2026, 1, 15)

    val rollingWindows: List<Int> = this["ROLLING_WINDOWS"]
        ?.split(",")?.map { it.trim().toInt() }
        ?: listOf(7, 14, 30)

    val calendarName: String = this["CALENDAR_NAME"] ?: "My Calendar"

    val smallSeizureColorIds: Set<String?> = this["SMALL_SEIZURE_COLOR_IDS"]
        ?.let { raw ->
            if (raw.isBlank()) setOf(null)
            else raw.split(",").map { it.trim() }.toSet()
        }
        ?: setOf(null)

    val drugColorIds: Set<String> = this["DRUG_COLOR_IDS"]
        ?.split(",")?.map { it.trim() }?.toSet()
        ?: setOf("2")

    val oneTimeDrugColorIds: Set<String> = this["ONETIME_DRUG_COLOR_IDS"]
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: emptySet()

    val bigSeizureColorId: String = this["BIG_SEIZURE_COLOR_ID"] ?: "3"

    val excludeDrugs: Set<String> = this["EXCLUDE_DRUGS"]
        ?.split(",")?.map { it.trim().lowercase() }?.toSet()
        ?: emptySet()

    val oauthPort: Int = this["OAUTH_PORT"]?.toInt() ?: 8888

    val googleClientId: String = this["GOOGLE_CLIENT_ID"]
        ?: error("GOOGLE_CLIENT_ID must be set in .env or environment")
    val googleClientSecret: String = this["GOOGLE_CLIENT_SECRET"]
        ?: error("GOOGLE_CLIENT_SECRET must be set in .env or environment")

    val credentialsDir: String = this["CREDENTIALS_DIR"] ?: "data/tokens"
    val csvOut: String = this["CSV_OUT"] ?: "data/daily.csv"
    val llmCsvOut: String = this["LLM_CSV_OUT"] ?: "data/llm-export.csv"
    val reportHtml: String = this["REPORT_HTML"] ?: "data/report.html"
    val summaryJsonOut: String = this["SUMMARY_JSON"] ?: "data/summary.json"
    val eventsOut: String = this["EVENTS_OUT"] ?: "data/seizure_events.csv"
    val eventsJsonOut: String = this["EVENTS_JSON_OUT"] ?: "data/events-{runId}.json"
}
