package seizureanalyzer.output

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.google.api.services.calendar.model.Event
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import seizureanalyzer.Config
import seizureanalyzer.calendar.toInstant
import seizureanalyzer.model.DailyRow
import seizureanalyzer.parsing.extractHour
import seizureanalyzer.parsing.formatNumber
import seizureanalyzer.parsing.slugify
import java.io.File

private val ROLLING_WINDOWS_LLM = listOf(7, 30)

internal fun writeLlmCsv(rows: List<DailyRow>, drugs: List<String>, outFile: File) {
    outFile.parentFile?.mkdirs()

    val backward = computeBackwardRolling(rows, ROLLING_WINDOWS_LLM)
    val regimenIds = computeRegimenIds(rows, drugs)

    val headers = buildList {
        add("date")
        add("dow")
        add("small")
        add("big")
        add("seizure_free")
        ROLLING_WINDOWS_LLM.forEach { w ->
            add("small_${w}d")
            add("big_${w}d")
        }
        drugs.forEach { drug ->
            val slug = slugify(drug)
            add("${slug}_mg")
            add("${slug}_mne")
        }
        add("regimen_id")
    }

    csvWriter().open(outFile) {
        writeRow(headers)
        rows.forEachIndexed { idx, row ->
            val values = mutableListOf<String>()
            values += row.date.toString()
            values += row.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            values += row.smallSeizures.toString()
            values += row.bigSeizures.toString()
            values += ((row.smallSeizures + row.bigSeizures) == 0).toString()
            ROLLING_WINDOWS_LLM.forEach { w ->
                val (s, b) = backward.getValue(w)[idx]
                values += s.toString()
                values += b.toString()
            }
            drugs.forEach { drug ->
                val dosage = row.drugDosages[drug]
                values += formatNumber(dosage?.total())
                values += (dosage?.formatTriple() ?: "")
            }
            values += regimenIds[idx].toString()
            writeRow(values)
        }
    }
}

private fun computeBackwardRolling(
    rows: List<DailyRow>,
    windows: List<Int>,
): Map<Int, List<Pair<Int, Int>>> {
    val smallPrefix = IntArray(rows.size + 1)
    val bigPrefix = IntArray(rows.size + 1)
    rows.forEachIndexed { i, r ->
        smallPrefix[i + 1] = smallPrefix[i] + r.smallSeizures
        bigPrefix[i + 1] = bigPrefix[i] + r.bigSeizures
    }
    return windows.associateWith { w ->
        rows.indices.map { i ->
            val from = (i + 1 - w).coerceAtLeast(0)
            val to = i + 1
            (smallPrefix[to] - smallPrefix[from]) to (bigPrefix[to] - bigPrefix[from])
        }
    }
}

private fun computeRegimenIds(rows: List<DailyRow>, drugs: List<String>): IntArray {
    val ids = IntArray(rows.size)
    for (i in 1 until rows.size) {
        val changed = drugs.any { rows[i].drugDosages[it] != rows[i - 1].drugDosages[it] }
        ids[i] = if (changed) ids[i - 1] + 1 else ids[i - 1]
    }
    return ids
}

internal fun writeEventsCsv(events: List<Event>, tz: TimeZone, outFile: File, echo: (String) -> Unit): String {
    outFile.parentFile?.mkdirs()

    val headers = listOf("id", "summary", "start", "end", "color_id", "hour_of_day", "location", "description")
    csvWriter().open(outFile) {
        writeRow(headers)
        events.forEach { event ->
            val start = event.start?.toInstant()?.toLocalDateTime(tz)?.toString() ?: ""
            val end = event.end?.toInstant()?.toLocalDateTime(tz)?.toString() ?: ""
            val isSeizure = event.colorId in Config.smallSeizureColorIds || event.colorId == Config.bigSeizureColorId
            val hour = if (isSeizure) extractHour(event.summary.orEmpty()).hour?.toString() ?: "" else ""
            writeRow(
                event.id.orEmpty(),
                event.summary.orEmpty(),
                start,
                end,
                event.colorId.orEmpty(),
                hour,
                event.location.orEmpty(),
                event.description.orEmpty(),
            )
        }
    }

    echo("Wrote ${events.size} raw events to ${outFile.absolutePath}")
    return outFile.absolutePath
}
