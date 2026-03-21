package seizureanalyzer.analysis

import com.google.api.services.calendar.model.Event
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import seizureanalyzer.Config
import seizureanalyzer.calendar.resolveDate
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DrugChange
import seizureanalyzer.parsing.parseDrugSummary

internal fun categorizeEvents(
    events: List<Event>,
    tz: TimeZone,
    echo: (String) -> Unit,
): CategorizedEvents {
    val drugChanges = mutableMapOf<String, MutableList<DrugChange>>()
    val detectedDrugs = sortedSetOf<String>()
    val smallSeizures = mutableMapOf<LocalDate, Int>()
    val bigSeizures = mutableMapOf<LocalDate, Int>()

    events.forEach { event ->
        val eventDate = event.resolveDate(tz)
        if (eventDate == null || eventDate < Config.analysisStart || eventDate > Config.analysisEnd) {
            return@forEach
        }

        when {
            event.colorId in Config.drugColorIds -> {
                val summary = event.summary.orEmpty()
                val parseResult = parseDrugSummary(summary)
                if (parseResult.matches.isEmpty()) {
                    echo(
                        "Skipping drug change event '$summary' (id=${event.id}, colorId=${event.colorId}) - unmatched dosage format. Segments=${parseResult.unmatchedSegments}"
                    )
                    return@forEach
                }

                parseResult.matches.forEach matches@{ parsed ->
                    if (parsed.name.lowercase() in Config.excludeDrugs) return@matches
                    detectedDrugs += parsed.name
                    drugChanges.getOrPut(parsed.name) { mutableListOf() }
                        .add(DrugChange(eventDate, parsed.dosage))
                }

                if (parseResult.unmatchedSegments.isNotEmpty()) {
                    echo(
                        "Event '$summary' (id=${event.id}, colorId=${event.colorId}) contained unmatched text: ${parseResult.unmatchedSegments.joinToString()}"
                    )
                }
            }

            event.colorId in Config.smallSeizureColorIds -> {
                smallSeizures[eventDate] = smallSeizures.getOrDefault(eventDate, 0) + 1
            }

            event.colorId == Config.bigSeizureColorId -> {
                bigSeizures[eventDate] = bigSeizures.getOrDefault(eventDate, 0) + 1
            }

            else -> {
                echo("Ignoring event '${event.summary}' (id=${event.id}) with unsupported colorId='${event.colorId}'")
            }
        }
    }

    drugChanges.values.forEach { it.sortBy { change -> change.date } }

    return CategorizedEvents(
        drugChanges = drugChanges,
        smallSeizuresByDate = smallSeizures,
        bigSeizuresByDate = bigSeizures,
        detectedDrugs = detectedDrugs,
    )
}
