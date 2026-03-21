package seizureanalyzer.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.api.services.calendar.model.Event
import kotlinx.datetime.Instant
import seizureanalyzer.ANALYSIS_END
import seizureanalyzer.ANALYSIS_START
import seizureanalyzer.ROLLING_WINDOWS
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow
import java.io.File

internal fun writeEventsJson(events: List<Event>, runId: Long, outFile: File, echo: (String) -> Unit): String {
    outFile.parentFile?.mkdirs()
    val mapper = ObjectMapper().registerModule(kotlinModule())

    val payload = mapOf(
        "run_id" to runId,
        "generated_at" to Instant.fromEpochMilliseconds(runId).toString(),
        "count" to events.size,
        "events" to events.map { event ->
            mapOf(
                "id" to event.id,
                "summary" to event.summary,
                "description" to event.description,
                "colorId" to event.colorId,
                "created" to event.created?.value,
                "updated" to event.updated?.value,
                "start" to event.start,
                "end" to event.end,
                "location" to event.location,
                "status" to event.status,
                "htmlLink" to event.htmlLink,
            )
        },
    )

    mapper.writeValue(outFile, payload)
    echo("Wrote JSON dump (${events.size} items) to ${outFile.absolutePath}")
    return outFile.absolutePath
}

internal fun writeChatGptSummary(
    rows: List<DailyRow>,
    categorized: CategorizedEvents,
    outFile: File,
): String {
    val mapper = ObjectMapper().registerModule(kotlinModule())
    outFile.parentFile?.mkdirs()

    val seizures = rows.map { row ->
        buildMap {
            put("date", row.date.toString())
            put("small", row.smallSeizures)
            put("big", row.bigSeizures)
            ROLLING_WINDOWS.forEach { window ->
                put("small_forward_${window}d", row.getForwardSmall(window))
                put("big_forward_${window}d", row.getForwardBig(window))
            }
        }
    }

    val drugTimelines = categorized.drugChanges.mapValues { (_, changes) ->
        changes.map { change ->
            mapOf(
                "date" to change.date.toString(),
                "morning" to change.dosage.morning,
                "noon" to change.dosage.noon,
                "evening" to change.dosage.evening,
                "total" to change.dosage.total(),
            )
        }
    }

    val seizureRollup: Map<String, Any> = buildMap {
        put("total_small", rows.sumOf { it.smallSeizures })
        put("total_big", rows.sumOf { it.bigSeizures })
        put("average_small_per_day", rows.map { it.smallSeizures }.average())
        put("average_big_per_day", rows.map { it.bigSeizures }.average())
        ROLLING_WINDOWS.forEach { window ->
            put("max_small_forward_${window}d", rows.maxOfOrNull { it.getForwardSmall(window) } ?: 0)
            put("max_big_forward_${window}d", rows.maxOfOrNull { it.getForwardBig(window) } ?: 0)
        }
    }

    val latestDrugState = rows.last().drugDosages.mapValues { (_, dosage) ->
        dosage?.let {
            mapOf(
                "total" to it.total(),
                "morning" to it.morning,
                "noon" to it.noon,
                "evening" to it.evening,
            )
        }
    }

    val payload: Map<String, Any> = mapOf(
        "analysis_window" to mapOf("start" to ANALYSIS_START.toString(), "end" to ANALYSIS_END.toString()),
        "seizure_rollup" to seizureRollup,
        "latest_drug_state" to latestDrugState,
        "daily_seizures" to seizures,
        "drug_changes" to drugTimelines,
        "detected_drugs" to categorized.detectedDrugs.sorted(),
        "run_metadata" to mapOf(
            "row_count" to rows.size,
            "drugs_tracked" to categorized.detectedDrugs.size,
        ),
    )

    mapper.writeValue(outFile, payload)
    return outFile.absolutePath
}
