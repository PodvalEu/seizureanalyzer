package seizureanalyzer.output

import com.google.api.services.calendar.model.Event
import kotlinx.datetime.Instant
import seizureanalyzer.Config
import seizureanalyzer.model.AnalysisResults
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow
import java.io.File

internal fun writeEventsJson(events: List<Event>, runId: Long, outFile: File, echo: (String) -> Unit): String {
    outFile.parentFile?.mkdirs()
    val mapper = JACKSON_MAPPER

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
    analysis: AnalysisResults,
    outFile: File,
): String {
    val mapper = JACKSON_MAPPER
    outFile.parentFile?.mkdirs()

    val seizures = rows.map { row ->
        buildMap {
            put("date", row.date.toString())
            put("small", row.smallSeizures)
            put("big", row.bigSeizures)
            Config.rollingWindows.forEach { window ->
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
        Config.rollingWindows.forEach { window ->
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

    val drugChangeImpacts = analysis.drugChangeImpacts.map { impact ->
        buildMap<String, Any?> {
            put("drug", impact.drug)
            put("date", impact.date.toString())
            put("dosage_before", impact.dosageBefore?.formatTriple())
            put("dosage_after", impact.dosageAfter.formatTriple())
            put("window_days", impact.windowDays)
            put("avg_daily_seizures_before", impact.avgDailySeizuresBefore)
            put("avg_daily_seizures_after", impact.avgDailySeizuresAfter)
            put("change_percent", impact.changePercent)
            put("confounded", impact.confounded)
        }
    }

    val regimenRanking = analysis.regimenRanking.map { regimen ->
        mapOf(
            "dosages" to regimen.dosages,
            "days" to regimen.days,
            "total_small" to regimen.totalSmall,
            "total_big" to regimen.totalBig,
            "avg_daily_total" to regimen.avgDailyTotal,
            "start_date" to regimen.startDate.toString(),
            "end_date" to regimen.endDate.toString(),
        )
    }

    val monthlyTrend = analysis.monthlyTrend.map { month ->
        mapOf(
            "year_month" to month.yearMonth,
            "total_small" to month.totalSmall,
            "total_big" to month.totalBig,
            "days" to month.daysWithData,
            "avg_daily_total" to month.avgDailyTotal,
            "seizure_free_days" to month.seizureFreeDays,
            "big_seizure_free_days" to month.bigSeizureFreeDays,
        )
    }

    val streaks = analysis.seizureFreeStreaks.map { streak ->
        mapOf(
            "start_date" to streak.startDate.toString(),
            "end_date" to streak.endDate.toString(),
            "days" to streak.days,
            "active_drugs" to streak.activeDrugs,
            "big_seizure_free_only" to streak.bigOnly,
        )
    }

    val correlations = analysis.drugCorrelations.map { corr ->
        mapOf(
            "drug" to corr.drug,
            "pearson_r" to corr.pearsonR,
            "days_on_drug" to corr.daysOnDrug,
            "days_off_drug" to corr.daysOffDrug,
            "avg_seizures_on_drug" to corr.avgSeizuresOnDrug,
            "avg_seizures_off_drug" to corr.avgSeizuresOffDrug,
        )
    }

    val payload: Map<String, Any> = mapOf(
        "analysis_window" to mapOf("start" to Config.analysisStart.toString(), "end" to Config.analysisEnd.toString()),
        "seizure_rollup" to seizureRollup,
        "latest_drug_state" to latestDrugState,
        "daily_seizures" to seizures,
        "drug_changes" to drugTimelines,
        "detected_drugs" to categorized.detectedDrugs.sorted(),
        "drug_change_impacts" to drugChangeImpacts,
        "regimen_ranking" to regimenRanking,
        "monthly_trend" to monthlyTrend,
        "seizure_free_streaks" to streaks,
        "drug_correlations" to correlations,
        "run_metadata" to mapOf(
            "row_count" to rows.size,
            "drugs_tracked" to categorized.detectedDrugs.size,
        ),
    )

    mapper.writeValue(outFile, payload)
    return outFile.absolutePath
}
