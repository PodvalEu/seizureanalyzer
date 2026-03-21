# Architecture

## Tech Stack
- Kotlin 1.9.25, JVM 21, Gradle 8.6
- Google Calendar API v3 with OAuth2 (CALENDAR_READONLY scope)
- Apache ECharts 5 (CDN, loaded in HTML report)
- Clikt 4.4.0 (CLI framework)
- Jackson (JSON), kotlin-csv (CSV output)
- kotlinx-datetime 0.6.1
- dotenv-kotlin for .env configuration
- Docker: gradle:8.6-jdk21 build -> eclipse-temurin:21-jre runtime

## Configuration (Config.kt)

All configuration via `Config` object, loaded from `.env` file (dotenv-kotlin) with defaults:

| Property | Env Var | Default |
|----------|---------|---------|
| `analysisStart` | `ANALYSIS_START` | `2024-01-01` |
| `analysisEnd` | `ANALYSIS_END` | `2026-01-15` |
| `rollingWindows` | `ROLLING_WINDOWS` | `7,14,30` |
| `calendarName` | `CALENDAR_NAME` | `My Calendar` |
| `smallSeizureColorIds` | `SMALL_SEIZURE_COLOR_IDS` | `null` (blank = setOf(null)) |
| `drugColorIds` | `DRUG_COLOR_IDS` | `2` |
| `bigSeizureColorId` | `BIG_SEIZURE_COLOR_ID` | `3` |
| `oauthPort` | `OAUTH_PORT` | `8888` |
| `googleClientId` | `GOOGLE_CLIENT_ID` | **required** |
| `googleClientSecret` | `GOOGLE_CLIENT_SECRET` | **required** |
| `credentialsDir` | `CREDENTIALS_DIR` | `data/tokens` |
| `csvOut` | `CSV_OUT` | `data/daily.csv` |
| `reportHtml` | `REPORT_HTML` | `data/report.html` |
| `summaryJsonOut` | `SUMMARY_JSON` | `data/summary.json` |
| `eventsOut` | `EVENTS_OUT` | `data/seizure_events.csv` |
| `eventsJsonOut` | `EVENTS_JSON_OUT` | `data/events-{runId}.json` |

`Constants.kt` only contains `JSON_FACTORY` (GsonFactory singleton).

## Package Structure (18 files)

```
src/main/kotlin/seizureanalyzer/
├── Config.kt            — Config object, .env-driven with defaults
├── Constants.kt         — JSON_FACTORY only
├── Main.kt              — CLI entry point (Clikt App), thin orchestrator
├── calendar/
│   ├── CalendarService.kt  — OAuth2 flow, buildCalendarService(), resolveCalendarId()
│   └── EventFetcher.kt     — listAllEvents() paginated, Event.resolveDate(), eventDateWithinRange()
├── model/
│   ├── Models.kt           — DrugDosage, DrugChange, CategorizedEvents, DailyRow
│   └── DrugParseModels.kt  — ParsedDrugLine, DrugParseResult, DoseSlot enum
├── parsing/
│   ├── DrugParser.kt       — parseDrugSummary(), parseSingleDose(), inferDoseSlot()
│   ├── DrugNormalizer.kt   — DRUG_ENTRY_PATTERN regex, normalizeDrugSummary(), normalizeDrugName() (12 mappings)
│   └── TextUtils.kt        — toDoubleValue(), formatNumber(), slugify()
├── analysis/
│   ├── EventCategorizer.kt — categorizeEvents() classifying by colorId via Config
│   ├── DailyRowBuilder.kt  — buildDailyRows(), generateDateRange()
│   └── RollingSum.kt       — applyForwardRolling() using prefix-sum arrays
└── output/
    ├── SharedMapper.kt     — JACKSON_MAPPER singleton (ObjectMapper + kotlinModule)
    ├── HtmlReportWriter.kt — writeHtmlReport() + buildHtmlTemplate() (THE main file for UI work)
    ├── CsvWriter.kt        — writeDailyCsv(), writeEventsCsv()
    ├── JsonWriter.kt       — writeEventsJson(), writeChatGptSummary()
    └── FileResolver.kt     — resolveReportHtmlFile() (auto-incrementing), resolveEventsJsonOut()
```

## Data Flow
1. `Main.kt` -> `CalendarService.buildCalendarService()` + `resolveCalendarId()`
2. `EventFetcher.listAllEvents()` -> paginated Google Calendar API fetch
3. `EventCategorizer.categorizeEvents()` -> classifies events by colorId into CategorizedEvents
4. `DailyRowBuilder.buildDailyRows()` -> one DailyRow per day with drug dosages + seizure counts
5. `RollingSum.applyForwardRolling()` -> prefix-sum arrays for 7/14/30 day forward windows
6. Five outputs: HTML report, daily CSV, events CSV, events JSON, summary JSON

## Event Classification (Google Calendar colorId)
- `in Config.drugColorIds` (default: "2") -> Drug change event (parsed for dosage info)
- `in Config.smallSeizureColorIds` (default: null) -> Small seizure (counted per day)
- `== Config.bigSeizureColorId` (default: "3") -> Big seizure (counted per day)
- Other -> Ignored with warning

## Drug Dosage Format
Calendar event summary: `💊 Drug morning-noon-evening` (e.g., "💊 Fycompa 0-0-6 mg")
Parsed via DRUG_ENTRY_PATTERN regex. Multi-drug events separated by `+`, `,`, `;`, or `and`.

## OAuth2 Configuration
Google Client ID and Secret are now in `.env` file (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET). The CalendarService builds credentials from these.

## File Versioning
`FileResolver.resolveReportHtmlFile()` auto-increments: report-1.html, report-2.html, etc.
