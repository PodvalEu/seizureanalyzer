package seizureanalyzer.output

import com.google.api.services.calendar.model.Event
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import seizureanalyzer.Config
import seizureanalyzer.calendar.toInstant
import seizureanalyzer.model.DailyRow
import seizureanalyzer.parsing.formatNumber
import seizureanalyzer.parsing.slugify
import java.io.File

internal fun writeDailyCsv(rows: List<DailyRow>, drugs: List<String>, outFile: File) {
    outFile.parentFile?.mkdirs()
    val headers = buildList {
        add("date")
        drugs.forEach { drug ->
            val slug = slugify(drug)
            add("drug_$slug")
            add("drug_${slug}_morning")
            add("drug_${slug}_noon")
            add("drug_${slug}_evening")
        }
        add("small_seizures")
        add("big_seizures")
        Config.rollingWindows.forEach { window ->
            add("small_seizures_forward_${window}d")
            add("big_seizures_forward_${window}d")
        }
    }

    com.github.doyaaaaaken.kotlincsv.dsl.csvWriter().open(outFile) {
        writeRow(headers)
        rows.forEach { row ->
            val values = mutableListOf<String>()
            values += row.date.toString()
            drugs.forEach { drug ->
                val dosage = row.drugDosages[drug]
                values += (dosage?.formatTriple() ?: "")
                values += formatNumber(dosage?.morning)
                values += formatNumber(dosage?.noon)
                values += formatNumber(dosage?.evening)
            }
            values += row.smallSeizures.toString()
            values += row.bigSeizures.toString()
            Config.rollingWindows.forEach { window ->
                values += row.getForwardSmall(window).toString()
                values += row.getForwardBig(window).toString()
            }
            writeRow(values)
        }
    }
}

internal fun writeLlmCsv(rows: List<DailyRow>, drugs: List<String>, outFile: File) {
    outFile.parentFile?.mkdirs()
    val headers = buildList {
        add("date")
        drugs.forEach { add("${it.lowercase()}_dosage_mg_morning_noon_evening") }
        add("small_seizure_count")
        add("big_seizure_count")
    }

    com.github.doyaaaaaken.kotlincsv.dsl.csvWriter().open(outFile) {
        writeRow(headers)
        rows.forEach { row ->
            val values = mutableListOf<String>()
            values += row.date.toString()
            drugs.forEach { drug ->
                values += (row.drugDosages[drug]?.formatTriple() ?: "")
            }
            values += row.smallSeizures.toString()
            values += row.bigSeizures.toString()
            writeRow(values)
        }
    }
}

internal fun writeEventsCsv(events: List<Event>, tz: TimeZone, outFile: File, echo: (String) -> Unit): String {
    outFile.parentFile?.mkdirs()

    val headers = listOf("id", "summary", "start", "end", "color_id", "location", "description")
    com.github.doyaaaaaken.kotlincsv.dsl.csvWriter().open(outFile) {
        writeRow(headers)
        events.forEach { event ->
            val start = event.start?.toInstant()?.toLocalDateTime(tz)?.toString() ?: ""
            val end = event.end?.toInstant()?.toLocalDateTime(tz)?.toString() ?: ""
            writeRow(
                event.id.orEmpty(),
                event.summary.orEmpty(),
                start,
                end,
                event.colorId.orEmpty(),
                event.location.orEmpty(),
                event.description.orEmpty(),
            )
        }
    }

    echo("Wrote ${events.size} raw events to ${outFile.absolutePath}")
    return outFile.absolutePath
}
