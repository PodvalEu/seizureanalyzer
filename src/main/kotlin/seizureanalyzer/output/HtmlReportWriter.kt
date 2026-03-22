package seizureanalyzer.output

import seizureanalyzer.Config
import seizureanalyzer.model.AnalysisResults
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow
import java.io.File

internal fun writeHtmlReport(
    rows: List<DailyRow>,
    drugs: List<String>,
    categorized: CategorizedEvents,
    analysis: AnalysisResults,
    baseFile: File,
): File {
    val outFile = resolveReportHtmlFile(baseFile)
    outFile.parentFile?.mkdirs()
    val mapper = JACKSON_MAPPER

    val labels = rows.map { it.date.toString() }

    val rawDrugData = drugs.associateWith { drug ->
        rows.map { row -> row.drugDosages[drug]?.total() }
    }
    val rawDrugDataJson = mapper.writeValueAsString(rawDrugData)

    val normalizedDrugData = drugs.associateWith { drug ->
        val rawValues = rawDrugData[drug]!!
        val maxValue = rawValues.filterNotNull().maxOrNull() ?: 0.0
        rawValues.map { value ->
            when {
                value == null -> null
                maxValue <= 0.0 -> 0.0
                else -> value / maxValue
            }
        }
    }

    // Drug series — maximally distinct hues (blue, green, orange, teal, magenta, brown)
    val drugColors = listOf("#2563eb", "#16a34a", "#ea580c", "#0891b2", "#c026d3", "#92400e")
    val drugSeries = drugs.mapIndexed { index, drug ->
        val color = drugColors[index % drugColors.size]
        mapOf(
            "name" to drug,
            "type" to "line",
            "smooth" to false,
            "step" to "end",
            "showSymbol" to false,
            "yAxisIndex" to 0,
            "connectNulls" to false,
            "emphasis" to mapOf("focus" to "series"),
            "lineStyle" to mapOf("width" to 2.5, "type" to "dashed", "color" to color),
            "itemStyle" to mapOf("color" to color),
            "data" to normalizedDrugData[drug],
        )
    }

    // Seizure series - only 30d visible by default
    // Big = bold red (dominant), Small = muted sage green (subdued background)
    data class SeizureStyle(val label: String, val smallColor: String, val bigColor: String, val totalColor: String, val defaultVisible: Boolean)
    val seizureStyles = mapOf(
        7 to SeizureStyle("7d", "#a3be8c", "#ef4444", "#6366f1", false),
        14 to SeizureStyle("14d", "#8faa7b", "#dc2626", "#818cf8", false),
        30 to SeizureStyle("30d", "#7a966a", "#b91c1c", "#4f46e5", true),
    )

    val seizureSeries = Config.rollingWindows.flatMap { window ->
        val style = seizureStyles[window]!!
        listOf(
            buildMap {
                put("name", "Small seizures ${style.label}")
                put("type", "line")
                put("smooth", true)
                put("showSymbol", false)
                put("yAxisIndex", 1)
                put("lineStyle", mapOf("width" to 2, "color" to style.smallColor, "type" to "dashed"))
                put("itemStyle", mapOf("color" to style.smallColor))
                put("areaStyle", mapOf("opacity" to 0.06))
                put("data", rows.map { it.getForwardSmall(window) })
                if (!style.defaultVisible) put("selected", false)
            },
            buildMap {
                put("name", "Big seizures ${style.label}")
                put("type", "line")
                put("smooth", true)
                put("showSymbol", false)
                put("yAxisIndex", 1)
                put("lineStyle", mapOf("width" to 3, "color" to style.bigColor))
                put("itemStyle", mapOf("color" to style.bigColor))
                put("areaStyle", mapOf("opacity" to 0.25))
                put("data", rows.map { it.getForwardBig(window) })
                if (!style.defaultVisible) put("selected", false)
            },
            buildMap {
                put("name", "All seizures ${style.label}")
                put("type", "line")
                put("smooth", true)
                put("showSymbol", false)
                put("yAxisIndex", 1)
                put("lineStyle", mapOf("width" to 1, "color" to style.totalColor, "type" to "dotted", "opacity" to 0.4))
                put("itemStyle", mapOf("color" to style.totalColor))
                put("data", rows.map { it.getForwardSmall(window) + it.getForwardBig(window) })
                if (!style.defaultVisible) put("selected", false)
            },
        )
    }

    val series = drugSeries + seizureSeries
    val labelsJson = mapper.writeValueAsString(labels)
    val seriesJson = mapper.writeValueAsString(series)

    // Build legend selected state: 7d and 14d off by default
    val legendSelected = mutableMapOf<String, Boolean>()
    seizureSeries.forEach { s ->
        val name = s["name"] as String
        if (s.containsKey("selected")) {
            legendSelected[name] = false
        }
    }
    val legendSelectedJson = mapper.writeValueAsString(legendSelected)

    // Drug legend names for grouped legend
    val drugLegendJson = mapper.writeValueAsString(drugs)

    // Seizure window groups: one toggle per window controls small+big+total
    val seizureWindowGroups = Config.rollingWindows.map { window ->
        val style = seizureStyles[window]!!
        mapOf(
            "label" to "Seizures ${style.label}",
            "smallName" to "Small seizures ${style.label}",
            "bigName" to "Big seizures ${style.label}",
            "totalName" to "All seizures ${style.label}",
            "smallColor" to style.smallColor,
            "bigColor" to style.bigColor,
            "totalColor" to style.totalColor,
            "defaultVisible" to style.defaultVisible,
        )
    }
    val seizureGroupsJson = mapper.writeValueAsString(seizureWindowGroups)

    // Summary stats
    val totalSmall = rows.sumOf { it.smallSeizures }
    val totalBig = rows.sumOf { it.bigSeizures }


    val lagCorrelationsJson = mapper.writeValueAsString(analysis.lagCorrelations.map { lc ->
        mapOf(
            "drug" to lc.drug,
            "lag" to lc.lagDays,
            "r" to lc.pearsonR,
            "n" to lc.sampleSize,
            "w" to lc.windowDays,
        )
    })

    val cusumCurveJson = mapper.writeValueAsString(
        seizureanalyzer.analysis.computeCusumCurve(rows).map { (date, value) ->
            mapOf("date" to date.toString(), "cusum" to value)
        }
    )

    val changePointsJson = mapper.writeValueAsString(analysis.changePoints.map { cp ->
        mapOf(
            "date" to cp.date.toString(),
            "direction" to cp.direction.name,
            "magnitude" to cp.magnitude,
            "cusum" to cp.cumulativeSum,
            "drugs" to cp.activeDrugs.entries.sortedBy { it.key }
                .joinToString(", ") { "${it.key} ${it.value}" },
            "drugChange" to cp.recentDrugChange,
        )
    })

    // Seizure events for hour-of-day and day-of-week charts
    val seizureEventsJson = mapper.writeValueAsString(categorized.seizureEvents.map { ev ->
        mapOf(
            "date" to ev.date.toString(),
            "hour" to ev.hour,
            "big" to ev.big,
        )
    })

    val html = buildHtmlTemplate(
        labelsJson, seriesJson, drugLegendJson, seizureGroupsJson,
        legendSelectedJson, rawDrugDataJson, totalSmall, totalBig,
        lagCorrelationsJson, cusumCurveJson, changePointsJson,
        seizureEventsJson,
    )
    outFile.writeText(html)
    return outFile
}

private fun buildHtmlTemplate(
    labelsJson: String,
    seriesJson: String,
    drugLegendJson: String,
    seizureGroupsJson: String,
    legendSelectedJson: String,
    rawDrugDataJson: String,
    totalSmall: Int,
    totalBig: Int,
    lagCorrelationsJson: String,
    cusumCurveJson: String,
    changePointsJson: String,
    seizureEventsJson: String,
): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Seizure Analyzer</title>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
    <style>
        *, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

        :root {
            --bg: #f8fafc;
            --surface: #ffffff;
            --border: #e2e8f0;
            --text: #0f172a;
            --text-muted: #94a3b8;
            --accent: #3b82f6;
            --accent-hover: #2563eb;
            --radius: 8px;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
            background: var(--bg);
            color: var(--text);
        }

        /* ── Top bar: title + stats + controls ── */
        .topbar {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 10px 20px;
            background: var(--surface);
            border-bottom: 1px solid var(--border);
            flex-shrink: 0;
            flex-wrap: wrap;
        }

        .topbar-brand {
            display: flex;
            align-items: baseline;
            gap: 10px;
            margin-right: auto;
        }

        .topbar-brand h1 {
            font-size: 15px;
            font-weight: 700;
            letter-spacing: -0.01em;
            white-space: nowrap;
        }

        .topbar-brand .meta {
            font-size: 12px;
            color: var(--text-muted);
            white-space: nowrap;
        }

        .stat {
            display: flex;
            align-items: baseline;
            gap: 5px;
            font-size: 12px;
            color: var(--text-muted);
            white-space: nowrap;
        }

        .stat strong {
            font-size: 16px;
            font-weight: 700;
            color: #dc2626;
            font-variant-numeric: tabular-nums;
        }

        .divider {
            width: 1px;
            height: 20px;
            background: var(--border);
            flex-shrink: 0;
        }

        /* ── Control strip ── */
        .controls {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 6px 20px;
            background: var(--surface);
            border-bottom: 1px solid var(--border);
            flex-shrink: 0;
            flex-wrap: wrap;
            min-height: 40px;
        }

        .ctrl-section {
            display: flex;
            align-items: center;
            gap: 4px;
        }

        .ctrl-label {
            font-size: 10px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.06em;
            color: var(--text-muted);
            margin-right: 2px;
        }

        .chip {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            padding: 3px 10px;
            border: 1px solid var(--border);
            border-radius: 100px;
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
            background: var(--surface);
            color: #475569;
            transition: all 0.12s;
            user-select: none;
            white-space: nowrap;
        }

        .chip:hover { background: #f1f5f9; border-color: #cbd5e1; }
        .chip.off { opacity: 0.35; }
        .chip.active { background: var(--accent); color: #fff; border-color: var(--accent); }

        .chip .swatch {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            flex-shrink: 0;
        }

        .chip .swatch-line {
            width: 14px;
            height: 0;
            border-top: 2.5px dashed var(--sw-color);
            flex-shrink: 0;
        }

        .chip .swatch-pair {
            display: flex;
            gap: 2px;
        }

        .chip .swatch-pair span {
            width: 6px;
            height: 6px;
            border-radius: 50%;
        }

        .spacer { flex: 1; min-width: 8px; }

        .text-btn {
            font-size: 11px;
            font-weight: 500;
            color: var(--accent);
            cursor: pointer;
            background: none;
            border: none;
            padding: 3px 6px;
            border-radius: 4px;
            transition: background 0.12s;
        }

        .text-btn:hover { background: #eff6ff; }

        /* ── Chart ── */
        .chart-wrap {
            padding: 12px 20px 20px;
            position: relative;
        }

        #reportChart {
            width: 100%;
            height: 75vh;
            min-height: 550px;
            max-height: 900px;
        }

        #crosshair {
            position: absolute;
            top: 0;
            bottom: 32px;
            width: 1px;
            background: #94a3b8;
            pointer-events: none;
            display: none;
            z-index: 50;
        }

        #custom-tooltip {
            position: absolute;
            pointer-events: none;
            display: none;
            z-index: 100;
            background: rgba(255,255,255,0.96);
            border: 1px solid #e2e8f0;
            border-radius: 6px;
            padding: 8px 12px;
            font-size: 12px;
            color: #0f172a;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            white-space: nowrap;
        }

        /* ── Tab bar ── */
        .tab-bar {
            display: flex;
            gap: 0;
            background: var(--surface);
            border-bottom: 2px solid var(--border);
            padding: 0 20px;
        }

        .tab-bar button {
            font-family: inherit;
            font-size: 13px;
            font-weight: 600;
            padding: 10px 20px;
            border: none;
            background: none;
            color: var(--text-muted);
            cursor: pointer;
            border-bottom: 2px solid transparent;
            margin-bottom: -2px;
            transition: color 0.15s, border-color 0.15s;
        }

        .tab-bar button:hover { color: var(--text); }
        .tab-bar button.active { color: var(--accent); border-bottom-color: var(--accent); }

        .tab-content { display: none; }
        .tab-content.active { display: block; }

        /* ── Lag tab ── */
        .lag-content {
            max-width: 1100px;
            margin: 0 auto;
            padding: 24px 20px;
        }

        .lag-content .note {
            font-size: 13px;
            color: var(--text-muted);
            font-style: italic;
            margin-bottom: 20px;
            line-height: 1.5;
        }

        .dist-row {
            display: flex;
            gap: 20px;
            padding: 0 20px 20px;
        }

        .dist-row > div {
            flex: 1;
            height: 350px;
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--radius);
        }

        #lagChart, #cusumChart {
            width: 100%;
            height: 400px;
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            margin-bottom: 24px;
        }

        .lag-content table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            font-variant-numeric: tabular-nums;
            background: var(--surface);
            border-radius: var(--radius);
            overflow: hidden;
            border: 1px solid var(--border);
        }

        .lag-content th {
            background: #f1f5f9;
            font-weight: 600;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.04em;
            color: #475569;
            padding: 8px 12px;
            text-align: left;
            border-bottom: 1px solid var(--border);
        }

        .lag-content td {
            padding: 7px 12px;
            border-bottom: 1px solid #f1f5f9;
        }

        .lag-content tr:last-child td { border-bottom: none; }
        .lag-content .good { color: #16a34a; }
        .lag-content .bad { color: #dc2626; }
        .lag-content .muted { color: var(--text-muted); }
        .lag-content .best { font-weight: 700; }
    </style>
</head>
<body>
    <!-- Top bar -->
    <div class="topbar">
        <div class="topbar-brand">
            <h1>Seizure Analyzer</h1>
            <span class="meta">${Config.analysisStart} &ndash; ${Config.analysisEnd}</span>
        </div>
        <div class="stat">
            <strong>${totalSmall + totalBig}</strong> seizures
            <span style="color:#cbd5e1">&middot;</span>
            ${totalSmall} small
            <span style="color:#cbd5e1">&middot;</span>
            ${totalBig} big
        </div>
    </div>

    <!-- Tab bar -->
    <div class="tab-bar" id="tabBar">
        <button class="active" data-tab="timeline">Timeline</button>
        <button data-tab="lag">Lag Analysis</button>
        <button data-tab="changepoints">Change Points</button>
    </div>

    <!-- Tab: Timeline -->
    <div id="tab-timeline" class="tab-content active">
        <!-- Controls -->
        <div class="controls" id="controls"></div>

        <!-- Chart -->
        <div class="chart-wrap">
            <div id="crosshair"></div>
            <div id="custom-tooltip"></div>
            <div id="reportChart"></div>
        </div>

        <!-- Distribution charts -->
        <div class="dist-row">
            <div id="hourChart"></div>
            <div id="dowChart"></div>
        </div>
    </div>

    <!-- Tab: Lag Analysis -->
    <div id="tab-lag" class="tab-content">
        <div class="lag-content">
            <p class="note">
                Medications don&rsquo;t work instantly &mdash; some take days or weeks to kick in.
                This asks: &ldquo;If we gave a drug today, do seizures drop 3 days later? 7 days? 14 days?&rdquo;
                For each drug and each delay, we take the dose on a given day and compare it to
                the total number of seizures over a window starting at that delay.
                The window size is different for each drug, matched to how long it takes to reach steady state
                (e.g. 5 days for Orfiril, 21 days for Fycompa).
                The strength of that link is measured as a score <b>r</b> (ranging from &minus;1 to +1).
                <b style="color:#16a34a">Negative r (green)</b> = when the dose goes up, seizures tend to go down &mdash; the drug seems to help.
                <b style="color:#dc2626">Positive r (red)</b> = seizures tend to go up with the dose &mdash; no benefit seen (or seizures were already rising when the dose was increased).
                The delay with the strongest green value tells you roughly how long that drug takes to show its effect.
                This is a statistical pattern, not proof of cause and effect.
            </p>
            <div id="lagChart"></div>
            <div id="lagTable"></div>
        </div>
    </div>

    <!-- Tab: Change Points -->
    <div id="tab-changepoints" class="tab-content">
        <div class="lag-content">
            <p class="note">
                <strong>What is this?</strong>
                The line goes up on bad days and down on good days. Where it drifts far enough
                to matter, a dot appears:
                <b style="color:#16a34a">green ▼</b> = things got better,
                <b style="color:#dc2626">red ▲</b> = things got worse.
                This finds when seizures <em>actually</em> shifted &mdash; which may or may not
                line up with a drug change. Dashed vertical lines show drug changes for comparison.
            </p>
            <p class="note">
                <strong>Reading the table:</strong>
                &ldquo;Magnitude&rdquo; = how big the shift was.
                &ldquo;Nearest Drug Change&rdquo; = the closest medication adjustment &mdash;
                if blank, the shift happened without any recent drug change.
            </p>
            <div id="cusumChart"></div>
            <div id="cusumTable"></div>
        </div>
    </div>

    <script>
        const labels = $labelsJson;
        const seriesData = $seriesJson;
        const drugNames = $drugLegendJson;
        const seizureGroups = $seizureGroupsJson;
        const defaultHidden = $legendSelectedJson;
        const rawDrugData = $rawDrugDataJson;

        const allNames = seriesData.map(s => s.name);
        seizureGroups.forEach(g => {
            if (!allNames.includes(g.smallName)) allNames.push(g.smallName);
            if (!allNames.includes(g.bigName)) allNames.push(g.bigName);
            if (!allNames.includes(g.totalName)) allNames.push(g.totalName);
        });

        const selected = {};
        allNames.forEach(n => { selected[n] = !(n in defaultHidden); });

        const chart = echarts.init(document.getElementById('reportChart'));

        // Separate drug and seizure series
        const drugSeriesArr = seriesData.filter(s => drugNames.includes(s.name));
        const seizureSeriesArr = seriesData.filter(s => !drugNames.includes(s.name));
        const drugColors = {};
        const laneHeight = 40;
        const numDrugs = drugNames.length;
        const laneTotal = laneHeight * numDrugs;

        const grids = [];
        const xAxes = [];
        const yAxes = [];
        const allSeries = [];

        // One grid per drug
        drugSeriesArr.forEach((ds, i) => {
            const color = ds.lineStyle?.color || '#999';
            drugColors[ds.name] = color;
            const topPx = i * laneHeight;
            grids.push({ left: 80, right: 50, top: topPx, height: laneHeight - 4, borderColor: '#e2e8f0', borderWidth: 0, backgroundColor: 'transparent' });
            xAxes.push({ type: 'category', gridIndex: i, data: labels, show: false, boundaryGap: false });
            yAxes.push({ type: 'value', gridIndex: i, min: 0, max: 1, show: false });
            allSeries.push({
                name: ds.name, type: 'line', xAxisIndex: i, yAxisIndex: i,
                data: ds.data, step: 'end', showSymbol: false, connectNulls: false,
                lineStyle: { width: 1.5, color: color },
                areaStyle: { color: color, opacity: 0.25 },
                itemStyle: { color: color }
            });
        });

        // Main seizure grid below drug lanes
        const mainTop = laneTotal + 8;
        grids.push({ left: 80, right: 50, top: mainTop, bottom: 32, borderColor: 'transparent' });
        const mainXIdx = xAxes.length;
        xAxes.push({
            type: 'category', gridIndex: numDrugs, data: labels, boundaryGap: false,
            axisLabel: { color: '#94a3b8', fontSize: 11, hideOverlap: true },
            axisLine: { lineStyle: { color: '#e2e8f0' } }, axisTick: { show: false }
        });
        const mainYIdx = yAxes.length;
        yAxes.push({
            type: 'value', gridIndex: numDrugs, position: 'right',
            axisLabel: { color: '#94a3b8', fontSize: 11 },
            splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
            minInterval: 1, max: null
        });

        // Assign seizure series to main grid
        seizureSeriesArr.forEach(s => {
            allSeries.push(Object.assign({}, s, { xAxisIndex: mainXIdx, yAxisIndex: mainYIdx }));
        });

        const axisIndices = xAxes.map((_, i) => i);

        const option = {
            backgroundColor: 'transparent', animationDuration: 300,
            legend: { show: false },
            tooltip: { show: false },
            grid: grids,
            xAxis: xAxes,
            yAxis: yAxes,
            dataZoom: [{ type: 'inside', xAxisIndex: axisIndices, throttle: 50 }],
            series: allSeries
        };

        // Graphic: drug name labels + lane separators
        option.graphic = drugNames.map((name, i) => ({
            type: 'text', left: 4, top: i * laneHeight + laneHeight / 2 - 6,
            style: { text: name, fill: drugColors[name] || '#666', fontSize: 11, fontWeight: 600, fontFamily: 'Inter, -apple-system, sans-serif' },
            z: 100
        }));
        for (let i = 1; i < numDrugs; i++) {
            option.graphic.push({ type: 'line', left: 80, right: 50, shape: { x1: 0, y1: 0, x2: 2000, y2: 0 }, top: i * laneHeight, style: { stroke: '#e2e8f0', lineWidth: 0.5 }, z: 99 });
        }
        option.graphic.push({ type: 'line', left: 80, right: 50, shape: { x1: 0, y1: 0, x2: 2000, y2: 0 }, top: laneTotal, style: { stroke: '#cbd5e1', lineWidth: 1 }, z: 99 });

        chart.setOption(option);

        function rebuildLayout() {
            let slot = 0;
            drugNames.forEach((name, i) => {
                if (selected[name]) {
                    option.grid[i] = { left: 80, right: 50, top: slot * laneHeight, height: laneHeight - 4, borderColor: '#e2e8f0', borderWidth: 0 };
                    option.series[i].data = drugSeriesArr[i].data;
                    slot++;
                } else {
                    option.grid[i] = { left: 80, right: 50, top: 0, height: 0, borderWidth: 0 };
                    option.series[i].data = [];
                }
            });
            const newMainTop = slot * laneHeight + (slot > 0 ? 8 : 0);
            option.grid[numDrugs] = Object.assign({}, option.grid[numDrugs], { top: newMainTop });

            const graphics = [];
            let visSlot = 0;
            drugNames.forEach((name, i) => {
                if (!selected[name]) return;
                graphics.push({ type: 'text', left: 4, top: visSlot * laneHeight + laneHeight / 2 - 6, style: { text: name, fill: drugColors[name] || '#666', fontSize: 11, fontWeight: 600, fontFamily: 'Inter, -apple-system, sans-serif' }, z: 100 });
                if (visSlot > 0) {
                    graphics.push({ type: 'line', left: 80, right: 50, shape: { x1: 0, y1: 0, x2: 2000, y2: 0 }, top: visSlot * laneHeight, style: { stroke: '#e2e8f0', lineWidth: 0.5 }, z: 99 });
                }
                visSlot++;
            });
            if (visSlot > 0) {
                graphics.push({ type: 'line', left: 80, right: 50, shape: { x1: 0, y1: 0, x2: 2000, y2: 0 }, top: visSlot * laneHeight, style: { stroke: '#cbd5e1', lineWidth: 1 }, z: 99 });
            }
            option.graphic = graphics;
            chart.setOption(option, { replaceMerge: ['graphic'] });
        }

        // Custom crosshair + tooltip spanning all grids
        const crosshair = document.getElementById('crosshair');
        const tooltip = document.getElementById('custom-tooltip');
        const chartEl = document.getElementById('reportChart');
        const wrapEl = document.querySelector('.chart-wrap');

        // Build lookup: seizure series raw data by name
        const seizureData = {};
        seizureSeriesArr.forEach(s => { seizureData[s.name] = s.data; });

        function findDateIndex(mouseX) {
            // Use the main x-axis to find the closest date index
            for (let i = 0; i < labels.length; i++) {
                const px = chart.convertToPixel({ xAxisIndex: mainXIdx }, labels[i]);
                if (px >= mouseX) return i > 0 && (mouseX - chart.convertToPixel({ xAxisIndex: mainXIdx }, labels[i - 1])) < (px - mouseX) ? i - 1 : i;
            }
            return labels.length - 1;
        }

        function buildTooltipHtml(idx) {
            let html = '<div style="font-weight:600;margin-bottom:6px;font-size:13px">' + labels[idx] + '</div>';
            // Drug dosages
            drugNames.forEach(name => {
                if (!selected[name]) return;
                const val = rawDrugData[name][idx];
                if (val == null) return;
                const color = drugColors[name] || '#999';
                const dot = '<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:' + color + ';margin-right:6px;vertical-align:middle"></span>';
                const display = val % 1 ? val.toFixed(1) : val;
                html += '<div style="display:flex;justify-content:space-between;gap:20px;font-size:12px;line-height:1.6"><span>' + dot + name + '</span><span style="font-weight:600;font-variant-numeric:tabular-nums">' + display + ' mg</span></div>';
            });
            // Seizure counts
            seizureSeriesArr.forEach(s => {
                if (!selected[s.name]) return;
                const val = s.data[idx];
                if (val == null) return;
                const color = s.itemStyle?.color || '#999';
                const dot = '<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:' + color + ';margin-right:6px;vertical-align:middle"></span>';
                html += '<div style="display:flex;justify-content:space-between;gap:20px;font-size:12px;line-height:1.6"><span>' + dot + s.name + '</span><span style="font-weight:600;font-variant-numeric:tabular-nums">' + val + '</span></div>';
            });
            return html;
        }

        chartEl.addEventListener('mousemove', (e) => {
            const rect = chartEl.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const gridLeft = chart.convertToPixel({ xAxisIndex: mainXIdx }, labels[0]);
            const gridRight = chart.convertToPixel({ xAxisIndex: mainXIdx }, labels[labels.length - 1]);
            if (x >= gridLeft && x <= gridRight) {
                crosshair.style.display = 'block';
                crosshair.style.left = (x + 20) + 'px';

                const idx = findDateIndex(x);
                tooltip.innerHTML = buildTooltipHtml(idx);
                tooltip.style.display = 'block';

                // Position tooltip: to the right of crosshair, flip if near edge
                const wrapRect = wrapEl.getBoundingClientRect();
                const tipWidth = tooltip.offsetWidth;
                const tipLeft = x + 20 + 12;
                if (tipLeft + tipWidth > wrapRect.width) {
                    tooltip.style.left = (x + 20 - tipWidth - 12) + 'px';
                } else {
                    tooltip.style.left = tipLeft + 'px';
                }
                tooltip.style.top = (e.clientY - rect.top - 20) + 'px';
            } else {
                crosshair.style.display = 'none';
                tooltip.style.display = 'none';
            }
        });
        chartEl.addEventListener('mouseleave', () => {
            crosshair.style.display = 'none';
            tooltip.style.display = 'none';
        });

        // Apply default hidden state
        Object.keys(defaultHidden).forEach(name => {
            chart.dispatchAction({ type: 'legendToggleSelect', name: name });
        });

        // ── Build control strip ──
        const ctrl = document.getElementById('controls');

        function mkLabel(text) {
            const el = document.createElement('span');
            el.className = 'ctrl-label';
            el.textContent = text;
            return el;
        }

        function mkSection() {
            const el = document.createElement('div');
            el.className = 'ctrl-section';
            return el;
        }

        function mkDivider() {
            const el = document.createElement('div');
            el.className = 'divider';
            el.style.height = '16px';
            el.style.margin = '0 6px';
            return el;
        }

        // Drugs
        const drugSec = mkSection();
        drugSec.appendChild(mkLabel('Drugs'));
        drugNames.forEach(name => {
            const s = seriesData.find(s => s.name === name);
            if (!s) return;
            const color = s.itemStyle?.color || s.lineStyle?.color || '#999';

            const chip = document.createElement('button');
            chip.className = 'chip' + (selected[name] ? '' : ' off');
            chip.innerHTML = '<span class="swatch-line" style="--sw-color:' + color + '"></span>' + name;

            chip.addEventListener('click', () => {
                selected[name] = !selected[name];
                chip.classList.toggle('off', !selected[name]);
                chart.dispatchAction({ type: 'legendToggleSelect', name: name });
                rebuildLayout();
            });
            drugSec.appendChild(chip);
        });
        ctrl.appendChild(drugSec);
        ctrl.appendChild(mkDivider());

        // Seizures
        const seizSec = mkSection();
        seizSec.appendChild(mkLabel('Seizures'));
        seizureGroups.forEach(g => {
            const chip = document.createElement('button');
            chip.className = 'chip' + (g.defaultVisible ? '' : ' off');
            chip.innerHTML =
                '<span class="swatch-pair">' +
                '<span style="background:' + g.smallColor + '"></span>' +
                '<span style="background:' + g.bigColor + '"></span>' +
                '</span>' + g.label;

            chip.addEventListener('click', () => {
                const nowVisible = !selected[g.smallName];
                selected[g.smallName] = nowVisible;
                selected[g.bigName] = nowVisible;
                selected[g.totalName] = nowVisible;
                chip.classList.toggle('off', !nowVisible);
                chart.dispatchAction({ type: 'legendToggleSelect', name: g.smallName });
                chart.dispatchAction({ type: 'legendToggleSelect', name: g.bigName });
                chart.dispatchAction({ type: 'legendToggleSelect', name: g.totalName });
            });
            seizSec.appendChild(chip);
        });
        ctrl.appendChild(seizSec);
        ctrl.appendChild(mkDivider());

        // Scale
        const scaleSec = mkSection();
        scaleSec.appendChild(mkLabel('Scale'));
        ['Auto','10','20','30','50'].forEach(v => {
            const chip = document.createElement('button');
            chip.className = 'chip' + (v === 'Auto' ? ' active' : '');
            chip.textContent = v;
            chip.dataset.max = v.toLowerCase();
            chip.addEventListener('click', () => {
                scaleSec.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                option.yAxis[mainYIdx].max = (v === 'Auto') ? null : Number(v);
                chart.setOption(option);
            });
            scaleSec.appendChild(chip);
        });
        ctrl.appendChild(scaleSec);

        // Range
        ctrl.appendChild(mkDivider());
        const rangeSec = mkSection();
        rangeSec.appendChild(mkLabel('Range'));
        const totalDays = labels.length;
        [['All', totalDays], ['1y', 365], ['180d', 180], ['90d', 90]].forEach(([label, days]) => {
            const chip = document.createElement('button');
            chip.className = 'chip' + (label === 'All' ? ' active' : '');
            chip.textContent = label;
            chip.addEventListener('click', () => {
                rangeSec.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                const startPct = Math.max(0, (1 - days / totalDays) * 100);
                chart.dispatchAction({ type: 'dataZoom', start: startPct, end: 100 });
            });
            rangeSec.appendChild(chip);
        });
        ctrl.appendChild(rangeSec);

        // Spacer + show/hide
        const spacer = document.createElement('div');
        spacer.className = 'spacer';
        ctrl.appendChild(spacer);

        const showBtn = document.createElement('button');
        showBtn.className = 'text-btn';
        showBtn.textContent = 'Show all';
        showBtn.addEventListener('click', () => {
            allNames.forEach(n => {
                if (!selected[n]) {
                    selected[n] = true;
                    chart.dispatchAction({ type: 'legendToggleSelect', name: n });
                }
            });
            ctrl.querySelectorAll('.chip:not([data-max])').forEach(el => el.classList.remove('off'));
            rebuildLayout();
        });
        ctrl.appendChild(showBtn);

        const hideBtn = document.createElement('button');
        hideBtn.className = 'text-btn';
        hideBtn.textContent = 'Hide all';
        hideBtn.addEventListener('click', () => {
            allNames.forEach(n => {
                if (selected[n]) {
                    selected[n] = false;
                    chart.dispatchAction({ type: 'legendToggleSelect', name: n });
                }
            });
            ctrl.querySelectorAll('.chip:not([data-max])').forEach(el => el.classList.add('off'));
            rebuildLayout();
        });
        ctrl.appendChild(hideBtn);

        window.addEventListener('resize', () => { chart.resize(); });

        // ── Hour-of-day and Day-of-week distribution charts ──
        const seizureEvents = $seizureEventsJson;

        // Hour-of-day histogram
        (function() {
            const hourLabels = [];
            for (let i = 0; i < 24; i++) hourLabels.push(i + ':00');
            hourLabels.push('N/A');

            const smallCounts = new Array(25).fill(0);
            const bigCounts = new Array(25).fill(0);
            seizureEvents.forEach(ev => {
                const idx = ev.hour != null ? ev.hour : 24;
                if (ev.big) bigCounts[idx]++;
                else smallCounts[idx]++;
            });

            const hourChart = echarts.init(document.getElementById('hourChart'));
            hourChart.setOption({
                title: { text: 'Seizures by Hour of Day', left: 'center', top: 8, textStyle: { fontSize: 14, fontWeight: 600, color: '#0f172a' } },
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                legend: { top: 32, textStyle: { fontSize: 11 } },
                grid: { top: 64, left: 50, right: 20, bottom: 32 },
                xAxis: {
                    type: 'category', data: hourLabels,
                    axisLabel: { fontSize: 10, interval: 0, rotate: 0,
                        formatter: function(v, i) { return i < 24 ? i : 'N/A'; }
                    },
                    axisTick: { alignWithLabel: true },
                },
                yAxis: {
                    type: 'value', minInterval: 1,
                    axisLabel: { fontSize: 11 },
                    splitLine: { lineStyle: { type: 'dashed', color: '#e2e8f0' } },
                },
                series: [
                    { name: 'Small', type: 'bar', stack: 'total', data: smallCounts, itemStyle: { color: '#7a966a' }, barMaxWidth: 20 },
                    { name: 'Big', type: 'bar', stack: 'total', data: bigCounts, itemStyle: { color: '#dc2626' }, barMaxWidth: 20 },
                ],
            });
            window.addEventListener('resize', () => hourChart.resize());
        })();

        // Day-of-week histogram
        (function() {
            const dowLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
            const smallCounts = new Array(7).fill(0);
            const bigCounts = new Array(7).fill(0);
            seizureEvents.forEach(ev => {
                const d = new Date(ev.date + 'T12:00:00');
                const dow = (d.getDay() + 6) % 7;
                if (ev.big) bigCounts[dow]++;
                else smallCounts[dow]++;
            });

            const dowChart = echarts.init(document.getElementById('dowChart'));
            dowChart.setOption({
                title: { text: 'Seizures by Day of Week', left: 'center', top: 8, textStyle: { fontSize: 14, fontWeight: 600, color: '#0f172a' } },
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                legend: { top: 32, textStyle: { fontSize: 11 } },
                grid: { top: 64, left: 50, right: 20, bottom: 32 },
                xAxis: {
                    type: 'category', data: dowLabels,
                    axisLabel: { fontSize: 11 },
                    axisTick: { alignWithLabel: true },
                },
                yAxis: {
                    type: 'value', minInterval: 1,
                    axisLabel: { fontSize: 11 },
                    splitLine: { lineStyle: { type: 'dashed', color: '#e2e8f0' } },
                },
                series: [
                    { name: 'Small', type: 'bar', stack: 'total', data: smallCounts, itemStyle: { color: '#7a966a' }, barMaxWidth: 40 },
                    { name: 'Big', type: 'bar', stack: 'total', data: bigCounts, itemStyle: { color: '#dc2626' }, barMaxWidth: 40 },
                ],
            });
            window.addEventListener('resize', () => dowChart.resize());
        })();

        const lagCorrelations = $lagCorrelationsJson;
        const cusumCurve = $cusumCurveJson;
        const changePointsData = $changePointsJson;

        // ── Tab switching ──
        let lagChartInstance = null;
        let cusumChartInstance = null;
        document.querySelectorAll('.tab-bar button').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.tab-bar button').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
                btn.classList.add('active');
                document.getElementById('tab-' + btn.dataset.tab).classList.add('active');

                if (btn.dataset.tab === 'timeline') {
                    chart.resize();
                }
                if (btn.dataset.tab === 'lag') {
                    if (!lagChartInstance) initLagTab();
                    else lagChartInstance.resize();
                }
                if (btn.dataset.tab === 'changepoints') {
                    if (!cusumChartInstance) initChangePointsTab();
                    else cusumChartInstance.resize();
                }
            });
        });

        // ── Lag Analysis tab ──
        function initLagTab() {
            const lagColors = ['#2563eb', '#16a34a', '#ea580c', '#0891b2', '#c026d3', '#92400e'];

            // Group by drug
            const byDrug = {};
            lagCorrelations.forEach(d => {
                if (!byDrug[d.drug]) byDrug[d.drug] = [];
                byDrug[d.drug].push(d);
            });
            const lagDrugs = Object.keys(byDrug).sort();
            const lags = [...new Set(lagCorrelations.map(d => d.lag))].sort((a, b) => a - b);

            // ECharts line chart
            lagChartInstance = echarts.init(document.getElementById('lagChart'));
            lagChartInstance.setOption({
                tooltip: {
                    trigger: 'axis',
                    formatter: params => {
                        let html = '<b>Lag ' + params[0].axisValue + '</b><br/>';
                        params.forEach(p => {
                            html += p.marker + ' ' + p.seriesName + ': <b>' + p.value.toFixed(3) + '</b><br/>';
                        });
                        return html;
                    }
                },
                legend: { top: 8, textStyle: { fontSize: 12 } },
                grid: { top: 50, left: 60, right: 30, bottom: 40 },
                xAxis: {
                    type: 'category',
                    data: lags.map(l => l + 'd'),
                    name: 'Lag (days)',
                    nameLocation: 'middle',
                    nameGap: 28,
                    axisLabel: { fontSize: 12 },
                },
                yAxis: {
                    type: 'value',
                    name: 'Pearson r',
                    nameLocation: 'middle',
                    nameGap: 42,
                    axisLabel: { fontSize: 11 },
                    splitLine: { lineStyle: { type: 'dashed', color: '#e2e8f0' } },
                },
                series: lagDrugs.map((drug, i) => ({
                    name: drug,
                    type: 'line',
                    data: lags.map(lag => {
                        const entry = byDrug[drug].find(d => d.lag === lag);
                        return entry ? entry.r : null;
                    }),
                    smooth: true,
                    symbol: 'circle',
                    symbolSize: 8,
                    lineStyle: { width: 2.5 },
                    itemStyle: { color: lagColors[i % lagColors.length] },
                })),
            });
            window.addEventListener('resize', () => { if (lagChartInstance) lagChartInstance.resize(); });

            // Table
            const el = document.getElementById('lagTable');
            if (lagCorrelations.length === 0) { el.innerHTML = '<p class="muted">No lag correlations computed.</p>'; return; }

            let html = '<table><tr><th>Drug</th><th>Window</th>';
            lags.forEach(l => { html += '<th>' + l + 'd lag</th>'; });
            html += '<th>Best lag</th><th>Samples</th></tr>';

            lagDrugs.forEach(drug => {
                const entries = byDrug[drug].sort((a, b) => a.lag - b.lag);
                let best = entries[0];
                entries.forEach(e => { if (e.r < best.r) best = e; });

                html += '<tr><td><strong>' + drug + '</strong></td>';
                html += '<td class="muted">' + entries[0].w + 'd</td>';
                entries.forEach(e => {
                    const cls = e.r < -0.1 ? 'good' : (e.r > 0.1 ? 'bad' : 'muted');
                    const bold = e.lag === best.lag ? ' best' : '';
                    html += '<td class="' + cls + bold + '">' + e.r.toFixed(3) + '</td>';
                });
                const bestCls = best.r < -0.1 ? 'good' : 'muted';
                html += '<td class="' + bestCls + ' best">' + best.lag + 'd (r=' + best.r.toFixed(3) + ')</td>';
                html += '<td class="muted">' + entries[0].n + '</td></tr>';
            });
            html += '</table>';
            el.innerHTML = html;
        }

        // ── Change Points tab ──
        function initChangePointsTab() {
            cusumChartInstance = echarts.init(document.getElementById('cusumChart'));

            // Scatter data for change points
            const decreaseData = [];
            const increaseData = [];
            changePointsData.forEach(cp => {
                const item = [cp.date, cp.cusum, cp.magnitude, cp.drugs, cp.drugChange, cp.direction];
                if (cp.direction === 'DECREASE') decreaseData.push(item);
                else increaseData.push(item);
            });

            const series = [
                {
                    name: 'CUSUM',
                    type: 'line',
                    data: cusumCurve.map(d => [d.date, d.cusum]),
                    smooth: true,
                    showSymbol: false,
                    lineStyle: { width: 2, color: '#3b82f6' },
                    itemStyle: { color: '#3b82f6' },
                },
                {
                    name: 'Improvement',
                    type: 'scatter',
                    data: decreaseData,
                    symbolSize: 14,
                    itemStyle: { color: '#16a34a' },
                    z: 10,
                },
                {
                    name: 'Worsening',
                    type: 'scatter',
                    data: increaseData,
                    symbolSize: 14,
                    itemStyle: { color: '#ef4444' },
                    z: 10,
                },
            ];

            cusumChartInstance.setOption({
                tooltip: {
                    trigger: 'item',
                    formatter: function(params) {
                        if (params.seriesType === 'line') {
                            return '<b>' + params.value[0] + '</b><br/>CUSUM: ' + params.value[1].toFixed(1);
                        }
                        const d = params.value;
                        let html = '<b>' + d[0] + '</b><br/>';
                        html += 'Direction: <b style="color:' + (d[5] === 'DECREASE' ? '#16a34a' : '#ef4444') + '">';
                        html += (d[5] === 'DECREASE' ? '▼ Improvement' : '▲ Worsening') + '</b><br/>';
                        html += 'Magnitude: ' + d[2].toFixed(1) + '<br/>';
                        html += 'Active drugs: ' + d[3] + '<br/>';
                        if (d[4]) html += 'Nearest drug change: ' + d[4];
                        return html;
                    }
                },
                legend: { top: 8, textStyle: { fontSize: 12 } },
                grid: { top: 50, left: 60, right: 30, bottom: 40 },
                xAxis: {
                    type: 'time',
                    axisLabel: { fontSize: 11 },
                },
                yAxis: {
                    type: 'value',
                    name: 'Cumulative Sum',
                    nameLocation: 'middle',
                    nameGap: 48,
                    axisLabel: { fontSize: 11 },
                    splitLine: { lineStyle: { type: 'dashed', color: '#e2e8f0' } },
                },
                series: series,
            });
            window.addEventListener('resize', () => { if (cusumChartInstance) cusumChartInstance.resize(); });

            // Table
            const el = document.getElementById('cusumTable');
            if (changePointsData.length === 0) {
                el.innerHTML = '<p class="muted">No significant change points detected.</p>';
                return;
            }

            let html = '<table><tr><th>Date</th><th>Direction</th><th>Magnitude</th><th>Active Drugs</th><th>Nearest Drug Change</th></tr>';
            changePointsData.forEach(cp => {
                const dirCls = cp.direction === 'DECREASE' ? 'good' : 'bad';
                const arrow = cp.direction === 'DECREASE' ? '▼ Improvement' : '▲ Worsening';
                html += '<tr>';
                html += '<td>' + cp.date + '</td>';
                html += '<td class="' + dirCls + '"><strong>' + arrow + '</strong></td>';
                html += '<td>' + cp.magnitude.toFixed(1) + '</td>';
                html += '<td>' + cp.drugs + '</td>';
                html += '<td>' + (cp.drugChange || '<span class="muted">none nearby</span>') + '</td>';
                html += '</tr>';
            });
            html += '</table>';
            el.innerHTML = html;
        }

    </script>
</body>
</html>
""".trimIndent()
