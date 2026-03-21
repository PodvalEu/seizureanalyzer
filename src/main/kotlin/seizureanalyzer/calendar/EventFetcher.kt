package seizureanalyzer.calendar

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import seizureanalyzer.ANALYSIS_END
import seizureanalyzer.ANALYSIS_START

internal fun listAllEvents(
    calendar: Calendar,
    calendarId: String,
    timeMin: Instant,
    timeMax: Instant,
): Sequence<Event> = sequence {
    var pageToken: String? = null
    do {
        val request = calendar.events().list(calendarId)
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .setTimeMin(DateTime(timeMin.toEpochMilliseconds()))
            .setTimeMax(DateTime(timeMax.toEpochMilliseconds()))
            .setPageToken(pageToken)

        val result = request.execute()
        for (event in result.items.orEmpty()) {
            yield(event)
        }
        pageToken = result.nextPageToken
    } while (pageToken != null)
}

internal fun eventDateWithinRange(event: Event, tz: TimeZone): Boolean {
    val date = event.start?.toLocalDate(tz) ?: event.created?.toLocalDate(tz)
    return date != null && date >= ANALYSIS_START && date <= ANALYSIS_END
}

internal fun DateTime.toLocalDate(tz: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this.value).toLocalDateTime(tz).date

internal fun EventDateTime.toLocalDate(tz: TimeZone): LocalDate? = this.toInstant()?.toLocalDateTime(tz)?.date

internal fun EventDateTime.toInstant(): Instant? = when {
    dateTime != null -> Instant.fromEpochMilliseconds(dateTime.value)
    date != null -> Instant.fromEpochMilliseconds(date.value)
    else -> null
}
