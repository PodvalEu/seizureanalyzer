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
import seizureanalyzer.analysis.runAnalysis
import seizureanalyzer.calendar.buildCalendarService
import seizureanalyzer.calendar.eventDateWithinRange
import seizureanalyzer.calendar.listAllEvents
import seizureanalyzer.calendar.resolveCalendarId
import seizureanalyzer.output.resolveEventsJsonOut
import seizureanalyzer.output.writeChatGptSummary
import seizureanalyzer.output.writeDailyCsv
import seizureanalyzer.output.writeLlmCsv
import seizureanalyzer.output.writeEventsCsv
import seizureanalyzer.output.writeEventsJson
import seizureanalyzer.output.writeHtmlReport
import java.io.File

class App : CliktCommand(name = "seizureanalyzer") {
    private val calendarIdOption by option("--calendar-id", help = "Calendar ID (or 'primary')")
    private val calendarName by option("--calendar-name", help = "Calendar display name to look up")
        .default(Config.calendarName)

    private val credentialsDir by option("--credentials-dir", help = "Directory to store OAuth tokens")
        .default(Config.credentialsDir)

    private val csvOut by option("--csv-out", help = "Daily aggregate CSV file path")
        .default(Config.csvOut)

    private val reportHtml by option("--report-html", help = "Standalone HTML report output path")
        .default(Config.reportHtml)

    private val summaryJsonOut by option("--summary-json", help = "ChatGPT-friendly summary JSON output path")
        .default(Config.summaryJsonOut)

    private val eventsOut by option("--events-out", help = "Raw events CSV output path")
        .default(Config.eventsOut)

    private val eventsJsonOut by option("--events-json-out", help = "JSON file capturing every downloaded event")
        .default(Config.eventsJsonOut)

    override fun run() {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val service = buildCalendarService(httpTransport, File(credentialsDir))

        val tz = TimeZone.currentSystemDefault()
        val calendarId = resolveCalendarId(service, calendarName, calendarIdOption, ::echo)

        val timeMin = Config.analysisStart.atStartOfDayIn(tz)
        val timeMax = Config.analysisEnd.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val allEvents = listAllEvents(service, calendarId, timeMin, timeMax).toList()
        echo("Fetched ${allEvents.size} events between ${Config.analysisStart} and ${Config.analysisEnd}")

        val runId = System.currentTimeMillis()
        val filteredEvents = allEvents.filter { eventDateWithinRange(it, tz) }
        val eventsJsonPath = writeEventsJson(filteredEvents, runId, File(resolveEventsJsonOut(eventsJsonOut, runId)), ::echo)
        val eventsCsvPath = writeEventsCsv(filteredEvents, tz, File(eventsOut), ::echo)

        val categorized = categorizeEvents(filteredEvents, tz, ::echo)
        val drugs = categorized.detectedDrugs.toList()  // already sorted (sortedSetOf)
        echo("Detected drugs: ${drugs.joinToString(", ")}")

        val dailyRows = buildDailyRows(categorized, Config.analysisStart, Config.analysisEnd)
        applyForwardRolling(dailyRows, Config.rollingWindows)

        val analysis = runAnalysis(dailyRows, categorized)
        echo("Analysis: ${analysis.drugChangeImpacts.size} drug change impacts, " +
            "${analysis.regimenRanking.size} regimens, " +
            "${analysis.monthlyTrend.size} months, " +
            "${analysis.seizureFreeStreaks.size} streaks")

        writeDailyCsv(dailyRows, drugs, File(csvOut))
        writeLlmCsv(dailyRows, drugs, categorized.seizureEvents, File(Config.llmCsvOut))
        val reportPath = writeHtmlReport(dailyRows, drugs, categorized, File(reportHtml))
        val summaryPath = writeChatGptSummary(dailyRows, categorized, analysis, File(summaryJsonOut))

        echo("Daily CSV: ${File(csvOut).absolutePath}")
        echo("LLM CSV: ${File(Config.llmCsvOut).absolutePath}")
        echo("HTML report: ${reportPath.absolutePath}")
        echo("Summary JSON: $summaryPath")
        echo("Events CSV: $eventsCsvPath")
        echo("Events JSON: $eventsJsonPath")
    }
}

fun main(args: Array<String>) {
    App().main(args)
}
