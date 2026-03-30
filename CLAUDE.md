# CLAUDE.md

- Always create a new git branch before making code changes in a new chat. Use a descriptive branch name based on the task.

## Project Overview

Seizure Analyzer — a Kotlin CLI app that reads epilepsy seizure and medication data from Google Calendar, runs statistical analyses, and produces an interactive HTML report with ECharts visualizations plus CSV/JSON exports for LLM analysis.

## Build & Run

```bash
# Build
./gradlew installDist

# Run locally
./build/install/seizureanalyzer/bin/seizureanalyzer

# Run via Docker
docker build -t seizureanalyzer .
docker run -v $(pwd)/data:/data seizureanalyzer
```

- JDK 21, Kotlin 1.9, Gradle 8.6
- Config lives in `config.env` (dotenv format)
- Output files: `data/daily.csv`, `data/report.html`, `data/summary.json`, `data/seizure_events.csv`

## Project Structure

```
src/main/kotlin/seizureanalyzer/
├── Main.kt, Config.kt, Constants.kt   # Entry point, config, constants
├── calendar/                           # Google Calendar OAuth + event fetching
├── parsing/                            # Drug name parsing/normalization, time extraction
├── model/                              # Data classes (Models.kt, DrugParseModels.kt)
├── analysis/                           # All analysis heuristics
│   ├── AnalysisRunner.kt               # Orchestrator — calls all analyzers
│   ├── EventCategorizer.kt             # Classifies calendar events by colorId
│   ├── DailyRowBuilder.kt              # Builds per-day rows
│   ├── RegimenAnalyzer.kt              # Drug regimen detection & ranking
│   ├── TrendAndCorrelation.kt          # Pearson r, linear regression
│   ├── RollingSum.kt                   # Rolling window computations
│   ├── LagAnalyzer.kt                  # Time-shifted drug-seizure correlations
│   ├── ChangePointDetector.kt          # CUSUM change-point detection
│   ├── VolatilityAnalyzer.kt           # CV, dispersion, burst detection
│   ├── TitrationAnalyzer.kt            # Titration trajectory scoring
│   ├── StreakAnalyzer.kt               # Seizure-free streak tracking
│   └── DrugImpactAnalyzer.kt           # Per-drug impact scoring
└── output/                             # Report generation
    ├── HtmlReportWriter.kt             # Main HTML report (ECharts, tab-based)
    ├── JsonWriter.kt                   # JSON export for LLM consumption
    ├── CsvWriter.kt                    # CSV export
    ├── FileResolver.kt                 # Output path resolution
    └── SharedMapper.kt                 # Jackson ObjectMapper config
```

## Key Patterns

- **Adding a new analysis**: Create analyzer in `analysis/`, add model to `Models.kt` (`AnalysisResults`), wire in `AnalysisRunner.runAnalysis()`, serialize in `JsonWriter`, add tab in `HtmlReportWriter`
- **HTML report**: Single-file HTML with inline CSS/JS, uses ECharts for all charts, tab-based navigation
- **Drug colors**: Drug-specific colors are assigned in the report and reused across tabs
- **All analysis specs**: See `TASKS.md` for detailed specs of each heuristic

## Code Style

- No test suite — verify by building and inspecting output
- Favor concise Kotlin idioms (data classes, extension functions, `let`/`map`/`filter` chains)
- Keep HtmlReportWriter sections self-contained per tab
