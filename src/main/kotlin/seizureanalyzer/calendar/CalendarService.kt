package seizureanalyzer.calendar

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import seizureanalyzer.Config
import seizureanalyzer.JSON_FACTORY
import java.io.File

internal fun buildCalendarService(httpTransport: NetHttpTransport, credentialsDir: File): Calendar {
    val clientSecrets = GoogleClientSecrets().apply {
        installed = GoogleClientSecrets.Details().apply {
            clientId = Config.googleClientId
            clientSecret = Config.googleClientSecret
            authUri = "https://accounts.google.com/o/oauth2/auth"
            tokenUri = "https://oauth2.googleapis.com/token"
            redirectUris = listOf("http://localhost")
        }
    }

    val flow = GoogleAuthorizationCodeFlow.Builder(
        httpTransport,
        JSON_FACTORY,
        clientSecrets,
        listOf(CalendarScopes.CALENDAR_READONLY),
    )
        .setDataStoreFactory(FileDataStoreFactory(credentialsDir))
        .setAccessType("offline")
        .build()

    val receiver = LocalServerReceiver.Builder().setPort(Config.oauthPort).build()
    val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

    return Calendar.Builder(httpTransport, JSON_FACTORY, credential)
        .setApplicationName("seizureanalyzer")
        .build()
}

internal fun resolveCalendarId(
    service: Calendar,
    calendarName: String?,
    calendarIdOption: String?,
    echo: (String) -> Unit,
): String {
    calendarName ?: return calendarIdOption ?: "primary"

    val calendars = service.calendarList().list().execute().items.orEmpty()
    val match = calendars.firstOrNull { it.summary.equals(calendarName, ignoreCase = true) }

    if (match == null) {
        val summaries = calendars.mapNotNull { it.summary }
        error(
            buildString {
                append("Calendar named '$calendarName' was not found in your account.")
                if (summaries.isNotEmpty()) {
                    append(" Available summaries: ${summaries.joinToString()}")
                }
            }
        )
    }

    echo("Using calendar '${match.summary}' (ID: ${match.id})")
    return match.id
}
