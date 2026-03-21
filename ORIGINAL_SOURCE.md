# Original Source Reference

The original application is located at:
```

```

## File Structure
```
epilepsy-analyzer/
├── app/
│   ├── src/main/kotlin/Main.kt          # Entire application (~950 lines)
│   ├── src/main/resources/.gitkeep
│   ├── build.gradle.kts                  # Gradle build config
│   ├── settings.gradle.kts               # Project name: "epilepsy-analyzer"
│   ├── gradle.properties                 # Kotlin code style: official
│   ├── Dockerfile                        # Multi-stage Docker build
│   ├── .dockerignore
│   ├── README.txt                        # Setup instructions
│   └── SPEC.md                           # Original specification
├── data/                                 # Output directory (volume-mounted)
│   ├── credentials.json                  # Google OAuth client credentials
│   ├── tokens/StoredCredential           # Cached OAuth tokens
│   ├── daily.csv                         # Aggregated daily data
│   ├── seizure_events.csv                # Raw event export
│   ├── events-{timestamp}.json           # Full event dumps
│   ├── report-{id}.html                  # Interactive charts
│   └── summary.json                      # ChatGPT-ready summary
└── (app/data/ also contains similar outputs from earlier runs)
```

## Package
```
local.epilepsyanalyzer
```

## Main Entry Point
```kotlin
fun main(args: Array<String>) {
    App().parse(args)  // Clikt CLI entry
}
```

## Key Constants
```kotlin
val ANALYSIS_START = LocalDate(2024, 1, 1)
val ANALYSIS_END = LocalDate(2026, 1, 15)
val ROLLING_WINDOWS = listOf(7, 14, 30)
val DRUG_COLOR_IDS = setOf("2")
```

## Functions Overview (Main.kt)

### Top-level Functions
| Function | Purpose |
|----------|---------|
| `buildCalendarService()` | OAuth2 auth + Calendar API client setup |
| `listAllEvents()` | Paginated event fetch (returns Sequence) |
| `parseDrugSummary()` | Parse drug name + dosages from event summary |
| `parseSingleDose()` | Fallback parser for single-dose entries |
| `inferDoseSlot()` | Determine morning/noon/evening from context |
| `normalizeDrugSummary()` | Unicode + unit normalization |
| `normalizeDrugName()` | Map raw names to canonical drug names |
| `generateDateRange()` | Create list of dates between start and end |
| `formatNumber()` | Format doubles (integers without decimals) |
| `slugify()` | Drug name → slug for CSV headers |

### Extension Functions
| Function | Purpose |
|----------|---------|
| `DateTime.toLocalDate()` | Google DateTime → kotlinx LocalDate |
| `EventDateTime.toLocalDate()` | Google EventDateTime → kotlinx LocalDate |
| `EventDateTime.toInstant()` | Google EventDateTime → kotlinx Instant |
| `String.toDoubleValue()` | Parse decimal with comma/dot support |

### App Class Methods (CliktCommand)
| Method | Purpose |
|--------|---------|
| `run()` | Main orchestration |
| `resolveCalendarId()` | Find calendar by name |
| `categorizeEvents()` | Classify events by colorId |
| `buildDailyRows()` | Build daily data with drug state tracking |
| `applyForwardRolling()` | Compute prefix-sum rolling windows |
| `writeDailyCsv()` | Output daily CSV |
| `writeHtmlReport()` | Generate ECharts HTML report |
| `writeEventsCsv()` | Export raw events as CSV |
| `writeEventsJson()` | Export raw events as JSON |
| `writeChatGptSummary()` | Generate ChatGPT-friendly JSON |
| `resolveReportHtmlFile()` | Auto-increment report file IDs |
| `resolveEventsJsonOut()` | Template-based JSON output paths |
| `eventDateWithinRange()` | Date range filter |

## Google Calendar Event Color Reference

The app relies on Google Calendar's built-in colorId system:
- Colors are set per-event in the Google Calendar UI
- The colorId is a string number ("1", "2", "3", etc.)
- Color mapping is calendar-specific and may vary between accounts

## Sample Data (from daily.csv headers)
Detected drugs in actual data: Diazepam, Fycompa, Lamictal, Ontozry, Topamax, Zonegran

## ECharts Report Configuration
- CDN: `https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js`
- Color palette for drugs: `["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"]`
- Seizure colors:
  - Small 7d: `#ff6384`, 14d: `#ff9f40`, 30d: `#d62728`
  - Big 7d: `#9966ff`, 14d: `#36a2eb`, 30d: `#9467bd`
