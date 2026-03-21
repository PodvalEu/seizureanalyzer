# Seizure Analyzer - Architecture

## Original Implementation

The original application was a single-file Kotlin application (~950 lines in `Main.kt`).

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.25 |
| Runtime | JVM | 21 |
| Build | Gradle | 8.6 |
| Container | Docker | Multi-stage |
| Calendar API | Google Calendar API v3 | rev20240111-2.0.0 |
| API Client | Google API Client | 2.6.0 |
| OAuth | Google OAuth Client + Jetty | 1.36.0 |
| CLI | Clikt | 4.4.0 |
| Date/Time | kotlinx-datetime | 0.6.1 |
| CSV | kotlin-csv-jvm | 1.10.0 |
| JSON | Jackson + Kotlin module | 2.17.1 |
| Charting | Apache ECharts 5 | CDN |

## Data Flow

```
Google Calendar API (OAuth2)
        │
        ▼
listAllEvents() ─── paginated fetch with time range
        │
        ▼
eventDateWithinRange() ─── filter to 2024-01-01 .. 2026-01-15
        │
        ▼
categorizeEvents() ─── classify by colorId:
        │               "2" → parse drug dosage from summary
        │               null → small seizure count++
        │               "3" → big seizure count++
        │               "1" → ignore (supplements)
        │
        ▼
buildDailyRows() ─── for each date in range:
        │               - track drug state (persists until changed)
        │               - collect seizure counts
        │
        ▼
applyForwardRolling() ─── prefix-sum 7/14/30-day windows
        │
        ▼
┌───────┼───────┬──────────┬──────────┬──────────┐
│       │       │          │          │          │
▼       ▼       ▼          ▼          ▼          ▼
daily  HTML    events    events    summary
.csv   report  .csv      .json     .json
```

## Data Models

```
DrugDosage(morning: Double, noon: Double, evening: Double)
  └─ total(): Double
  └─ formatTriple(): String  // "100-0-150"

DrugChange(date: LocalDate, dosage: DrugDosage)

CategorizedEvents(
  drugChanges: Map<String, List<DrugChange>>,  // drug name → sorted changes
  smallSeizuresByDate: Map<LocalDate, Int>,
  bigSeizuresByDate: Map<LocalDate, Int>,
  detectedDrugs: Set<String>
)

DailyRow(
  date: LocalDate,
  drugDosages: Map<String, DrugDosage?>,
  smallSeizures: Int,
  bigSeizures: Int,
  forwardSums: MutableMap<Int, Pair<Int, Int>>  // window → (small, big)
)

ParsedDrugLine(name: String, dosage: DrugDosage)
DrugParseResult(matches: List<ParsedDrugLine>, unmatchedSegments: List<String>)
```

## CLI Options

| Flag | Default | Description |
|------|---------|-------------|
| `--calendar-id` | `"primary"` | Calendar ID |
| `--calendar-name` | `"My Calendar"` | Calendar display name lookup |
| `--credentials-dir` | `"/data/tokens"` | OAuth token storage directory |
| `--csv-out` | `"/data/daily.csv"` | Daily aggregate CSV path |
| `--report-html` | `"/data/report.html"` | HTML report output path |
| `--summary-json` | `"/data/summary.json"` | ChatGPT summary JSON path |
| `--events-out` | `"/data/seizure_events.csv"` | Raw events CSV path |
| `--events-json-out` | `"/data/events-{runId}.json"` | Events JSON dump path |

## Docker Setup

```dockerfile
# Build stage
FROM gradle:8.6-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon installDist

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/install/epilepsy-analyzer /app
COPY src/main/resources /app/resources
ENTRYPOINT ["/app/bin/epilepsy-analyzer"]
```

Run:
```bash
docker build -t epilepsy-analyzer ./app
docker run --rm -it -p 8888:8888 -v "$PWD/data":/data epilepsy-analyzer
```

Volume mount `./data` provides:
- `credentials.json` (input)
- `tokens/` (persisted OAuth tokens)
- All output files

## Key Implementation Details

### Drug Summary Parsing Regex
```regex
^\s*(?:[\p{So}\p{Sk}]|💊)?\s*(.+?)\s+(\d+(?:[.,]\d+)?)(?:\s*(?:mg|ml|kapky|drops))?\s*[-–]\s*(\d+(?:[.,]\d+)?)(?:\s*(?:mg|ml|kapky|drops))?\s*[-–]\s*(\d+(?:[.,]\d+)?)(?:\s*(?:mg|ml|kapky|drops))?\s*$
```

### Text Normalization Pipeline
1. Unicode NFKC normalization
2. En-dash → hyphen replacement
3. Whitespace normalization around units (mg, ml)
4. Czech "kapka/kapky" → "drops" normalization

### Drug Name Normalization
- Unicode NFKD decomposition
- Emoji removal
- Leading non-alphanumeric removal
- Lowercase matching to canonical names (handles typos like "zonegraran")
- Czech language support (diacritical marks, Czech terms)

### Forward Rolling Sum Algorithm
Uses prefix-sum arrays for efficient O(n) computation per window size. Looks forward from each date (not backward), which means recent dates have partial windows.
