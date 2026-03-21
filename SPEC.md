# Seizure Analyzer - Specification

## 1. Purpose

Track epilepsy seizures and medication dosages for a patient to:
- Correlate drug dosage changes with seizure frequency patterns
- Enable ChatGPT analysis of the data for deeper insights (e.g., negative drug correlations)
- Visualize trends via interactive HTML reports

## 2. Data Source

All seizure and medication data is stored in a **Google Calendar** (configurable via `CALENDAR_NAME`).

### Authentication
- OAuth2 with Google Calendar API (CALENDAR_READONLY scope)
- Credentials: `/data/credentials.json` (OAuth client JSON from Google Cloud Console)
- Token storage: `/data/tokens/StoredCredential`
- First-time auth opens browser on port 8888 for OAuth consent

### Event Classification via colorId

| colorId | Meaning | Details |
|---------|---------|---------|
| `"2"` | Drug change | Summary format: `💊 {drug} {morning}-{noon}-{evening}` |
| `null` | Small seizure | Eye/head twitching |
| `"3"` | Big seizure | Limb shaking, arm/leg going limp |
| `"1"` | Supplements | Ignored (multivitamins, etc.) |
| other | Unknown | Logged and ignored |

### Event Date
- Primary: `event.start.date.value` (epoch milliseconds)
- Fallback: `event.created` timestamp

### Analysis Window
- Start: 2024-01-01
- End: 2026-01-15

## 3. Drug Dosage Parsing

### Standard Pattern
```
💊 {drug name} {morning}{unit?}-{noon}{unit?}-{evening}{unit?}
```
- Units: mg, ml, kapky/drops (optional)
- Multiple drugs per event separated by: "and", "+", ",", ";"
- Regex handles emoji prefixes, diacritical marks, Unicode normalization

### Known Drug Name Normalization
| Raw Input Patterns | Normalized Name |
|-------------------|-----------------|
| fycompa | Fycompa |
| lamictal | Lamictal |
| ontozry | Ontozry |
| topamax | Topamax |
| zonegran, zonegram, zonegraran | Zonegran |
| diazepam | Diazepam |
| magn* | Magnesium |
| vitamin d | Vitamin D |
| viridikid, viridian | Viridikid Multivitamin |
| gs vápník, vapnik | Calcium Magnesium Zinc |
| olej z trescich jater, cod liver | Cod Liver Oil |

## 4. Analysis Algorithms

### Daily Row Building
For each date in the analysis window:
1. Track current drug dosages (once set, persists until next change)
2. Count small and big seizures for that date
3. Consume drug change events as their dates arrive (queue-based)

### Forward-Looking Rolling Sums
For windows of 7, 14, and 30 days:
- Uses prefix-sum arrays for O(n) computation
- Sums seizure counts from current date forward (not backward)
- Stores both small and big seizure sums per window

### Drug Dosage Normalization (for visualization)
- Each drug normalized independently: value / max_total_dosage
- Range: 0.0 to 1.0 (percentage of that drug's maximum)
- null if drug hasn't been introduced yet

## 5. Output Files

### 1. HTML Report (`/data/report-{id}.html`)
- Standalone interactive chart using **Apache ECharts 5** (CDN)
- Dual Y-axes: drug dosages (left, normalized 0-1) and seizure counts (right)
- Drug curves: dashed lines, 10-color palette
- Seizure curves: solid lines with area fill
  - Small seizures: red (7d), orange (14d), dark red (30d)
  - Big seizures: purple (7d), blue (14d), muted purple (30d)
- Features: data zoom (scroll/pinch/slider), legend toggle, tooltip, save as image, seizure axis max control
- Auto-incremented file IDs to preserve history

### 2. Daily CSV (`/data/daily.csv`)
Columns:
- `date`
- `drug_{slug}` (total), `drug_{slug}_morning`, `drug_{slug}_noon`, `drug_{slug}_evening` (per drug)
- `small_seizures`, `big_seizures`
- `small_seizures_forward_7d`, `big_seizures_forward_7d`
- `small_seizures_forward_14d`, `big_seizures_forward_14d`
- `small_seizures_forward_30d`, `big_seizures_forward_30d`

### 3. Events CSV (`/data/seizure_events.csv`)
Raw events: id, summary, start, end, color_id, location, description

### 4. Events JSON (`/data/events-{runId}.json`)
Full Google Calendar event payloads with run metadata (run_id, generated_at, count)

### 5. Summary JSON (`/data/summary.json`)
ChatGPT-friendly format containing:
- `analysis_window`: start/end dates
- `seizure_rollup`: totals, averages, max rolling sums
- `latest_drug_state`: current dosages per drug
- `daily_seizures`: per-date counts with rolling sums
- `drug_changes`: per-drug timeline of dosage changes
- `detected_drugs`: sorted list of all identified drugs
- `run_metadata`: row count, drugs tracked

## 6. Detected Drugs (from actual data)
Diazepam, Fycompa, Lamictal, Ontozry, Topamax, Zonegran
