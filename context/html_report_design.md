# HTML Report Design

## Current State (HtmlReportWriter.kt)

The report is a single-page HTML file with embedded CSS+JS and Apache ECharts 5 (CDN).

### Layout Structure
1. **Top bar** — Title "Seizure Analyzer", date range, total seizure count
2. **Control strip** — Drug chips, seizure window chips, scale presets, range presets, show/hide all
3. **Chart** — Single ECharts instance with dual Y-axes (left=drugs 0-1, right=seizures)

### Kotlin Data Preparation (STABLE — preserve this)
- Normalizes drug dosages to 0-1 range per drug (each drug's max = 1.0)
- Builds drug series as dashed step lines with distinct colors
- Builds seizure series as smooth lines with area fill (opacity 0.06)
- Pairs small+big seizures per rolling window (from Config.rollingWindows)
- Outputs: `labelsJson`, `seriesJson`, `drugLegendJson`, `seizureGroupsJson`, `legendSelectedJson`, `totalSmall`, `totalBig`

### HTML Template (`buildHtmlTemplate()`)
- CSS variables: `--bg: #f8fafc`, `--surface: #fff`, `--border: #e2e8f0`, `--accent: #3b82f6`
- Control strip built dynamically in JS
- Drug chips: dashed line swatch + name, toggle on/off
- Seizure chips: paired dots (small+big colors), grouped toggle
- Scale chips: Auto/10/20/30/50, sets yAxis[1].max
- Range chips: All/1y/180d/90d, uses dataZoom
- Uses `Config.analysisStart` and `Config.analysisEnd` for date display

### Drug Colors (in order of drugs list)
1. Blue: #2563eb
2. Green: #16a34a
3. Orange: #ea580c
4. Teal: #0891b2
5. Magenta: #c026d3
6. Brown: #92400e

### Seizure Colors by Window
| Window | Small (red) | Big (purple) | Default Visible |
|--------|-------------|-------------|-----------------|
| 7d     | #f87171     | #a78bfa     | No              |
| 14d    | #ef4444     | #8b5cf6     | No              |
| 30d    | #dc2626     | #7c3aed     | Yes             |

## Design Evolution (10+ iterations)
1. Initial basic report with stacked cards
2-3. Restructured to top bar + control strip + chart
4. Removed "current medication" card
5. Improved color differentiation between small/big seizures
6. Redesigned seizure max axis as preset buttons
7. Grouped seizure toggles (one button per window controls small+big)
8. Improved drug color distinctness
9-10. Full redesign to compact toolbar layout
11. Code quality cleanup (shared ObjectMapper, named constants, Config object, .env)

## NEXT: Swim Lanes Drug Visualization
See `next_task_lanes_implementation.md` for the complete implementation plan.
