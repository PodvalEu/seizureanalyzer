# New Analysis Heuristics — Implementation Guide

Each is a self-contained task. Order matters only slightly — #1 is a prerequisite for #2.

The HTML report uses ECharts and a tab-based layout. Each new heuristic gets its own tab in the report with an appropriate visualization. The tab content is built in `HtmlReportWriter.kt` — add the JSON data for the new analysis alongside the existing ones, and add a new tab panel with the chart/table JS code.

## ~~1. Lag Analysis (time-shifted correlations)~~ DONE
**In plain English:** Medications don't work instantly — some take days or weeks to kick in. This checks "if we gave a drug today, does the seizure count drop 3 days later? 7 days? 14 days?" For each drug, it finds the delay where seizures drop the most, telling us how long that drug actually takes to have an effect.
**File:** `LagAnalyzer.kt`
**What it does:** Computes Pearson r between each drug's daily dose and seizure counts at multiple time offsets (0, 3, 7, 14, 21 days). Reuses existing `pearsonR()` from `TrendAndCorrelation.kt` (extract to shared util). For each drug, pairs `(dose on day i, seizure sum on days i+lag .. i+lag+7)`. Needs new rolling windows in `RollingSum.kt` or direct slice computation.
**Model:** `LagCorrelation(drug, lagDays, pearsonR, sampleSize)`
**Output:** Table per drug showing r at each lag. The lag with strongest negative r suggests optimal response time.
**Wire up:** Add to `AnalysisResults`, call from `AnalysisRunner.runAnalysis()`, serialize in `JsonWriter.writeChatGptSummary()`.
**Visualization:** New tab "Lag Analysis". ECharts **grouped bar chart** — X axis = lag offsets (0d, 3d, 7d, 14d, 21d), one bar group per drug, Y axis = Pearson r. Color-coded per drug using existing `drugColors`. Bars going below zero (negative r) indicate the drug helps at that lag. Highlight the best lag per drug with a marker or bold outline.

## ~~2. Change-Point Detection (CUSUM)~~ DONE
**In plain English:** Instead of assuming seizures changed because we changed a drug on a specific date, this asks "when did things actually get better or worse?" by looking at the seizure numbers themselves. It finds the exact dates where the seizure pattern shifted — sometimes that lines up with a drug change, sometimes it reveals something else was going on (illness, stress, sleep).
**File:** `ChangePointDetector.kt`
**What it does:** Runs cumulative sum (CUSUM) on the daily total seizure time series. Compute daily residuals `(seizures_today - global_mean)`, accumulate into cumulative sum S. Significant change points are where S reaches local max/min — these are dates where the seizure rate genuinely shifted. Threshold: flag changes where `|S_peak - S_trough| > 2 * stddev * sqrt(n)`.
**Model:** `ChangePoint(date, direction: INCREASE|DECREASE, magnitude, activeDrugs, recentDrugChange: String?)`
**Output:** List of detected change points with what drugs were active/recently changed. Cross-reference with `drugChanges` to annotate which drug change (if any) likely caused it.
**Wire up:** Same pattern — `AnalysisResults`, `AnalysisRunner`, `JsonWriter`.
**Visualization:** New tab "Change Points". ECharts **line chart** with the CUSUM curve (cumulative sum over time) as the main line. Detected change points marked as large colored dots on the curve — green for DECREASE (improvement), red for INCREASE (worsening). Each dot has a tooltip showing the date, magnitude, active drugs, and the nearest drug change if any. Overlay existing drug change dates as vertical dashed lines for visual cross-referencing.

## ~~3. Cyclical Pattern Analysis~~ DONE (skipped)
**In plain English:** Are seizures more likely on certain days of the week (e.g., Mondays after a tiring weekend) or certain days of the month (e.g., around medication refills)? This groups all seizures by weekday and by day-of-month to see if there's a pattern hiding in the routine.
**File:** `CyclicalAnalyzer.kt`
**What it does:** Two sub-analyses:
- **Day-of-week:** Group all rows by `dayOfWeek`, compute avg seizures per weekday. Chi-squared test against uniform distribution to check significance.
- **Day-of-month:** Same grouping by day-of-month (1–31). Detect if seizures cluster around specific days (e.g., medication refill gaps, routine disruptions).
**Model:** `CyclicalPattern(type: WEEKLY|MONTHLY, buckets: Map<String, Double>, chiSquared, pValue, peakBucket, troughBucket)`
**Output:** Bar chart data (day -> avg seizures) + significance flag.
**Wire up:** Same pattern.
**Visualization:** New tab "Cycles". Two ECharts **bar charts** side by side: (1) Day-of-week — 7 bars (Mon–Sun), Y = avg daily seizures. (2) Day-of-month — 31 bars (1–31), Y = avg daily seizures. Color the peak bar red and trough bar green. Show a subtitle with the chi-squared p-value and whether the pattern is statistically significant (p < 0.05) or not.

## 4. Volatility / Clustering Analysis
**In plain English:** Two drug regimens might both average 1 seizure per day, but one has steady 1-per-day while the other has 0 for a week then 7 in one day. This tells them apart. It measures how predictable/unpredictable seizures are under each regimen, and flags "burst" episodes — clusters of bad days in a row — along with what drugs were active during each burst.
**File:** `VolatilityAnalyzer.kt`
**What it does:**
- **Coefficient of variation (CV):** Per regimen, compute `stddev / mean` of daily seizure counts. Low CV = stable; high CV = unpredictable bursts.
- **Dispersion index:** `variance / mean` — if > 1, seizures are over-dispersed (clustered/bursty). If ~= 1, Poisson-like (random). If < 1, regular.
- **Burst detection:** Run-length encoding — find consecutive days with seizures > mean + 1.5 * stddev. Report burst periods with active drugs.
**Model:** `VolatilityStats(regimenDosages, cv, dispersionIndex, bursts: List<Burst>)` where `Burst(startDate, endDate, days, totalSeizures, activeDrugs)`
**Output:** Per-regimen volatility scores + list of burst episodes.
**Wire up:** Same pattern.
**Visualization:** New tab "Volatility". Two panels: (1) ECharts **scatter plot** — X axis = avg daily seizures, Y axis = coefficient of variation, one dot per regimen. Dot size = number of days on that regimen. Hover tooltip shows the drug combo and dates. Bottom-left quadrant (low seizures, low volatility) is the sweet spot — shade it green. (2) **Timeline strip** below the scatter — a horizontal bar spanning the full date range, with burst episodes highlighted as red blocks. Hover shows burst details (dates, seizure count, active drugs).

## 5. Tapering/Titration Trajectory Scoring
**In plain English:** When a drug is increased or decreased, the doctor does it in steps over days or weeks. This scores each ramp-up or ramp-down: was it done fast or slow? Did seizures get worse during the transition? Did things stabilize after? Answers questions like "Was the Topamax taper too fast?" or "Did ramping up Fycompa slowly actually help?"
**File:** `TitrationAnalyzer.kt`
**What it does:** For each drug, identify all titration sequences (consecutive dose changes in the same direction — increasing or decreasing). Score each trajectory:
- **Pace:** days between dose steps (fast < 7 days, normal 7-14, slow > 14)
- **Seizure trend during ramp:** slope of linear regression on daily seizures across the titration period
- **Stability after:** avg seizures in the 14 days after titration ended vs. during
Group by drug and direction (up-titration vs. taper). Answers: "Was the Topamax taper too fast?"
**Model:** `TitrationTrajectory(drug, direction: UP|DOWN, steps: List<DrugChange>, startDate, endDate, paceCategory, seizureSlopeDuring, avgSeizuresDuring, avgSeizuresAfter)`
**Output:** Per-drug titration history with pace and outcome scores.
**Wire up:** Same pattern. Uses `categorized.drugChanges` as primary input.
**Visualization:** New tab "Titrations". ECharts **step-line chart** per drug (switchable via dropdown or sub-tabs). X axis = time, Y left axis = dosage (step line showing the ramp), Y right axis = daily seizures (smoothed line overlay). Each titration sequence is a highlighted band on the X axis — green if seizures improved after, red if worsened, yellow if unchanged. Below the chart, a summary table: drug, direction (up/down arrow icon), pace (fast/normal/slow tag), seizure slope during, avg seizures during vs. after.

## 6. Regimen Stability Index
**File:** Extend existing `RegimenAnalyzer.kt`
**In plain English:** Some drug combos lasted months (the doctor was happy enough to keep them), others were abandoned after 2 weeks (something wasn't working). This scores each regimen by combining: how many seizures, how long it lasted, and why it ended. A combo that lasted 60 days with few seizures and wasn't urgently changed is clearly better than one abandoned after 10 days.
**What it does:** Add a `stabilityIndex` to `RegimenStats`. Calculated as:
- `durationScore = log2(days)` — longer regimens score higher
- `forcedChange = true` if the regimen was followed by a dose increase or new drug addition (suggests it wasn't working)
- `voluntaryEnd = true` if followed by a decrease or removal (suggests side effects or successful taper)
- `stabilityIndex = avgDailyTotal * (1 + if(forcedChange) 0.5 else 0) / durationScore`
Lower is better. A regimen that lasted 60 days with low seizures and wasn't force-changed scores best.
**Model:** Add `stabilityIndex, forcedChange, endReason: FORCED|VOLUNTARY|ONGOING` to `RegimenStats`.
**Output:** Existing regimen ranking, re-sorted by stability index.
**Wire up:** Modify `RegimenAnalyzer.kt` and `RegimenStats` model.
**Visualization:** Enhance existing Regimens tab (or new tab "Regimen Ranking"). ECharts **horizontal bar chart** — one bar per regimen, sorted by stability index (best at top). Bar length = stability index. Color-coded by end reason: green = ONGOING, blue = VOLUNTARY, red = FORCED. Each bar label shows the drug combo. Tooltip shows full details: days, avg seizures, end reason, dates. A vertical dashed line at the median stability index for reference.
