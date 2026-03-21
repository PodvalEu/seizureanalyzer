package seizureanalyzer

import io.github.cdimascio.dotenv.dotenv
import kotlinx.datetime.LocalDate

internal object Config {
    private val dotenv = dotenv { ignoreIfMissing = true }

    val analysisStart: LocalDate = dotenv["ANALYSIS_START"]?.let { LocalDate.parse(it) }
        ?: LocalDate(2024, 1, 1)

    val analysisEnd: LocalDate = dotenv["ANALYSIS_END"]?.let { LocalDate.parse(it) }
        ?: LocalDate(2026, 1, 15)

    val rollingWindows: List<Int> = dotenv["ROLLING_WINDOWS"]
        ?.split(",")?.map { it.trim().toInt() }
        ?: listOf(7, 14, 30)

    val calendarName: String = dotenv["CALENDAR_NAME"] ?: "My Calendar"

    val smallSeizureColorIds: Set<String?> = dotenv["SMALL_SEIZURE_COLOR_IDS"]
        ?.let { raw ->
            if (raw.isBlank()) setOf(null)
            else raw.split(",").map { it.trim() }.toSet()
        }
        ?: setOf(null)

    val drugColorIds: Set<String> = dotenv["DRUG_COLOR_IDS"]
        ?.split(",")?.map { it.trim() }?.toSet()
        ?: setOf("2")

    val bigSeizureColorId: String = dotenv["BIG_SEIZURE_COLOR_ID"] ?: "3"

    val excludeDrugs: Set<String> = dotenv["EXCLUDE_DRUGS"]
        ?.split(",")?.map { it.trim().lowercase() }?.toSet()
        ?: emptySet()

    val oauthPort: Int = dotenv["OAUTH_PORT"]?.toInt() ?: 8888

    val googleClientId: String = dotenv["GOOGLE_CLIENT_ID"]
        ?: error("GOOGLE_CLIENT_ID must be set in .env or environment")
    val googleClientSecret: String = dotenv["GOOGLE_CLIENT_SECRET"]
        ?: error("GOOGLE_CLIENT_SECRET must be set in .env or environment")

    val credentialsDir: String = dotenv["CREDENTIALS_DIR"] ?: "data/tokens"
    val csvOut: String = dotenv["CSV_OUT"] ?: "data/daily.csv"
    val reportHtml: String = dotenv["REPORT_HTML"] ?: "data/report.html"
    val summaryJsonOut: String = dotenv["SUMMARY_JSON"] ?: "data/summary.json"
    val eventsOut: String = dotenv["EVENTS_OUT"] ?: "data/seizure_events.csv"
    val eventsJsonOut: String = dotenv["EVENTS_JSON_OUT"] ?: "data/events-{runId}.json"
}
