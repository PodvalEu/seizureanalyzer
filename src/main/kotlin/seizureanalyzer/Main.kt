package seizureanalyzer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import seizureanalyzer.analysis.applyForwardRolling
import seizureanalyzer.analysis.buildDailyRows
import seizureanalyzer.analysis.categorizeEvents
import seizureanalyzer.calendar.buildCalendarService
import seizureanalyzer.calendar.eventDateWithinRange
import seizureanalyzer.calendar.listAllEvents
import seizureanalyzer.calendar.resolveCalendarId
import seizureanalyzer.output.resolveEventsJsonOut
import seizureanalyzer.output.writeChatGptSummary
import seizureanalyzer.output.writeDailyCsv
import seizureanalyzer.output.writeEventsCsv
import seizureanalyzer.output.writeEventsJson
import seizureanalyzer.output.writeHtmlReport
import java.io.File

class App : CliktCommand(name = "seizureanalyzer") {
    private val calendarIdOption by option("--calendar-id", help = "Calendar ID (or 'primary')")
    private val calendarName by option("--calendar-name", help = "Calendar display name to look up")
        .default("My Calendar")

    private val credentialsDir by option("--credentials-dir", help = "Directory to store OAuth tokens")
        .default("/data/tokens")

    private val csvOut by option("--csv-out", help = "Daily aggregate CSV file path")
        .default("/data/daily.csv")

    private val reportHtml by option("--report-html", help = "Standalone HTML report output path")
        .default("/data/report.html")

    private val summaryJsonOut by option("--summary-json", help = "ChatGPT-friendly summary JSON output path")
        .default("/data/summary.json")

    private val eventsOut by option("--events-out", help = "Raw events CSV output path")
        .default("/data/seizure_events.csv")

    private val eventsJsonOut by option("--events-json-out", help = "JSON file capturing every downloaded event")
        .default("/data/events-{runId}.json")

    override fun run() {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val service = buildCalendarService(httpTransport, File(credentialsDir))

        val tz = TimeZone.currentSystemDefault()
        val calendarId = resolveCalendarId(service, calendarName, calendarIdOption, ::echo)

        val timeMin = ANALYSIS_START.atStartOfDayIn(tz)
        val timeMax = ANALYSIS_END.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val allEvents = listAllEvents(service, calendarId, timeMin, timeMax).toList()
        echo("Fetched ${allEvents.size} events between $ANALYSIS_START and $ANALYSIS_END")

        val runId = System.currentTimeMillis()
        val filteredEvents = allEvents.filter { eventDateWithinRange(it, tz) }
        val eventsJsonPath = writeEventsJson(filteredEvents, runId, File(resolveEventsJsonOut(eventsJsonOut, runId)), ::echo)
        val eventsCsvPath = writeEventsCsv(filteredEvents, tz, File(eventsOut), ::echo)

        val categorized = categorizeEvents(filteredEvents, tz, ::echo)
        val drugs = categorized.detectedDrugs.toList()  // already sorted (sortedSetOf)
        echo("Detected drugs: ${drugs.joinToString(", ")}")

        val dailyRows = buildDailyRows(categorized, ANALYSIS_START, ANALYSIS_END)
        applyForwardRolling(dailyRows, ROLLING_WINDOWS)

        writeDailyCsv(dailyRows, drugs, File(csvOut))
        val reportPath = writeHtmlReport(dailyRows, drugs, File(reportHtml))
        val summaryPath = writeChatGptSummary(dailyRows, categorized, File(summaryJsonOut))

        echo("Daily CSV: ${File(csvOut).absolutePath}")
        echo("HTML report: ${reportPath.absolutePath}")
        echo("Summary JSON: $summaryPath")
        echo("Events CSV: $eventsCsvPath")
        echo("Events JSON: $eventsJsonPath")
    }
}

fun main(args: Array<String>) {
    App().main(args)
}
