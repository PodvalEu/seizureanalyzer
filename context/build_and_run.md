# Build and Run

## Prerequisites
Create a `.env` file in the project root with at minimum:
```
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
```

All other config has defaults (see architecture.md for the full Config table).

## Build
```bash
cd .
./gradlew --no-daemon installDist
```

## Run Locally
```bash
./build/install/seizureanalyzer/bin/seizureanalyzer
```
All paths default from Config/.env. CLI options override:
```bash
./build/install/seizureanalyzer/bin/seizureanalyzer \
  --credentials-dir data/tokens \
  --csv-out data/daily.csv \
  --report-html data/report.html \
  --summary-json data/summary.json \
  --events-out data/seizure_events.csv \
  --events-json-out "data/events-{runId}.json"
```
OAuth2 opens browser for first auth; subsequent runs use stored tokens in `data/tokens/`.

## Docker
```bash
docker build -t seizureanalyzer .
docker run -v $(pwd)/data:/data --env-file .env seizureanalyzer
```

## Output Files (in data/ directory)
| File | Description |
|------|-------------|
| `report-N.html` | Interactive HTML report (auto-incrementing N) |
| `daily.csv` | Daily aggregate CSV with drug dosages + rolling sums |
| `seizure_events.csv` | Raw events CSV |
| `events-{timestamp}.json` | Full JSON dump of all calendar events |
| `summary.json` | ChatGPT-friendly summary with rollups and drug timelines |

## Preview Workflow
```bash
cd data && python3 -m http.server 8080
```
Then open `localhost:8080/report-N.html` in browser.

## Git History
```
841f84b Extract configuration into .env file with dotenv-kotlin
d0f04f5 Clean up code quality: shared ObjectMapper, named constants, deduplicated logic
04ec554 Add modular seizure analyzer with interactive HTML report
f1680d4 Initial commit
```
