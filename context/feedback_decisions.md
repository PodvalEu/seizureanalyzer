# Design Decisions

## Core Decisions
- **Kotlin** (1.9.25, JVM 21)
- **Docker + local** runs both supported
- **Date range** configurable via .env (default 2024-01-01 to 2026-01-15)
- **Forward-looking rolling sums** — drug changes happen on a date, the goal is to see seizure counts going forward from that change
- **Modular structure** — original 950-line single file decomposed into 18 files across 6 packages
- **Same 5 outputs** (HTML report, daily CSV, events CSV, events JSON, summary JSON)
- **Config via .env** — all constants moved to Config object with dotenv-kotlin

## HTML Report Design Decisions (established through 10+ feedback rounds)
- **Modern light design** — clean, minimal, Inter font, slate/blue palette
- **Legend toggle** to hide/show any curve (ECharts-based but with custom control strip)
- **Seizure window toggles are grouped** — one button toggles both small+big per window (7d/14d/30d)
- **Only 30d visible by default**, 7d and 14d hidden initially
- **Drug colors**: maximally distinct — blue (#2563eb), green (#16a34a), orange (#ea580c), teal (#0891b2), magenta (#c026d3), brown (#92400e)
- **Seizure colors**: small = red tones (#f87171/#ef4444/#dc2626), big = purple/violet (#a78bfa/#8b5cf6/#7c3aed)
- **Seizure scale**: pill preset buttons (Auto, 10, 20, 30, 50)
- **Range**: preset buttons (All, 1y, 180d, 90d) — no bottom slider
- **Show all / Hide all** text buttons
- **No "current medication" card**
- **Total seizures stat** in top bar (e.g., "506 seizures · 333 small · 173 big")
- **Chart height**: 75vh, min 550px, max 900px for lanes variant

## Drug Visualization — DECIDED: Swim Lanes (Variant C)
The chosen approach is **swim lanes** — separate mini-rows per drug above the main seizure chart.
- Each drug gets its own horizontal lane (40px height)
- Drugs rendered as area fills (step line + area, opacity 0.25) within their lane
- Drug name labels positioned to the left of each lane (colored, 11px, bold)
- Lane separators: thin #e2e8f0 lines between lanes, thicker #cbd5e1 separator before seizures
- Main seizure chart sits below all drug lanes
- All grids share linked dataZoom (inside type, synced across all X axes)
- Drug Y axes hidden (0-1 normalized), seizure Y axis on right side

This replaces the previous approach of normalized dashed lines on a shared 0-1 axis, which made all drugs look identical at the top.
