# Seizure Analyzer

Correlates epilepsy seizure frequency with medication dosage changes by pulling data from Google Calendar and generating interactive HTML reports, CSVs, and JSON summaries.

## How it works

1. Fetches events from a Google Calendar (drug changes, small/big seizures)
2. Parses drug dosages from event summaries (e.g. `💊 Lamictal 100-0-150mg`)
3. Builds daily rows with seizure counts and active drug dosages
4. Computes rolling-window sums (7, 14, 30 days)
5. Outputs an interactive HTML report, daily CSV, raw events CSV, and a ChatGPT-friendly JSON summary

## Prerequisites

- Java 21+
- A Google Cloud project with the Calendar API enabled
- OAuth 2.0 credentials (client ID + secret)

## Setup

Configuration is split into two files:

- **`config.env`** — non-secret settings (analysis window, output paths)
- **`.env`** — secrets and personal settings (OAuth credentials, calendar name) — gitignored

Create both from the provided templates:

```bash
cp config.env.example config.env
cp .env.example .env
```

Edit `.env` with your credentials and calendar name:

| Variable | Description |
|---|---|
| `GOOGLE_CLIENT_ID` | OAuth 2.0 client ID *(required)* |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 client secret *(required)* |
| `CALENDAR_NAME` | Google Calendar name *(required)* |

Edit `config.env` to adjust the analysis window and calendar settings:

| Variable | Description | Default |
|---|---|---|
| `ANALYSIS_START` | Start date | `2024-01-01` |
| `ANALYSIS_END` | End date | `2026-01-15` |
| `ROLLING_WINDOWS` | Comma-separated window sizes | `7,14,30` |
| `DRUG_COLOR_IDS` | Calendar colorIds for drug events | `2` |
| `BIG_SEIZURE_COLOR_ID` | Calendar colorId for big seizures | `3` |
| `SMALL_SEIZURE_COLOR_IDS` | Calendar colorIds for small seizures | *(uncolored)* |
| `EXCLUDE_DRUGS` | Comma-separated drug names to hide | *(none)* |

## Run

### Local

```bash
./gradlew --no-daemon installDist
./build/install/seizureanalyzer/bin/seizureanalyzer
```

On first run, a browser window opens for Google OAuth. The token is cached in `data/tokens/`.

### Docker

```bash
docker build -t seizureanalyzer .
docker run -v $(pwd)/data:/data --env-file .env seizureanalyzer
```

## Output

All files go to `data/` by default:

| File | Description |
|---|---|
| `report.html` | Interactive chart (ECharts) with drug swim lanes and seizure rolling windows |
| `daily.csv` | One row per day: drug dosages, seizure counts, rolling sums |
| `seizure_events.csv` | Raw calendar events |
| `summary.json` | Structured summary for LLM analysis |
| `events-{runId}.json` | Full Google Calendar API payloads |

## Calendar event format

Drug changes use the summary format:

```
💊 DrugName morning-noon-evening[mg|ml]
```

Examples: `💊 Lamictal 100-0-150`, `💊 Fycompa 8-0-8 and Lamictal 100-0-150`

Seizures are distinguished by Google Calendar color ID (configurable in `config.env`).

## Tech stack

Kotlin 1.9 / Java 21, Gradle 8.6, Google Calendar API, Clikt, Apache ECharts, Jackson, kotlinx-datetime
