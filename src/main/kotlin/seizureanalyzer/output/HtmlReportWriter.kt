package seizureanalyzer.output

import seizureanalyzer.ANALYSIS_END
import seizureanalyzer.ANALYSIS_START
import seizureanalyzer.ROLLING_WINDOWS
import seizureanalyzer.model.DailyRow
import java.io.File

internal fun writeHtmlReport(
    rows: List<DailyRow>,
    drugs: List<String>,
    baseFile: File,
): File {
    val outFile = resolveReportHtmlFile(baseFile)
    outFile.parentFile?.mkdirs()
    val mapper = JACKSON_MAPPER

    val labels = rows.map { it.date.toString() }

    val normalizedDrugData = drugs.associateWith { drug ->
        val rawValues = rows.map { row -> row.drugDosages[drug]?.total() }
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
    // Small = red tones, Big = purple/violet tones — clearly distinct within each window
    data class SeizureStyle(val label: String, val smallColor: String, val bigColor: String, val defaultVisible: Boolean)
    val seizureStyles = mapOf(
        7 to SeizureStyle("7d", "#f87171", "#a78bfa", false),
        14 to SeizureStyle("14d", "#ef4444", "#8b5cf6", false),
        30 to SeizureStyle("30d", "#dc2626", "#7c3aed", true),
    )

    val seizureSeries = ROLLING_WINDOWS.flatMap { window ->
        val style = seizureStyles[window]!!
        listOf(
            buildMap {
                put("name", "Small seizures ${style.label}")
                put("type", "line")
                put("smooth", true)
                put("showSymbol", false)
                put("yAxisIndex", 1)
                put("lineStyle", mapOf("width" to 2, "color" to style.smallColor))
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
                put("lineStyle", mapOf("width" to 2, "color" to style.bigColor))
                put("itemStyle", mapOf("color" to style.bigColor))
                put("areaStyle", mapOf("opacity" to 0.06))
                put("data", rows.map { it.getForwardBig(window) })
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

    // Seizure window groups: one toggle per window controls both small+big
    val seizureWindowGroups = ROLLING_WINDOWS.map { window ->
        val style = seizureStyles[window]!!
        mapOf(
            "label" to "Seizures ${style.label}",
            "smallName" to "Small seizures ${style.label}",
            "bigName" to "Big seizures ${style.label}",
            "smallColor" to style.smallColor,
            "bigColor" to style.bigColor,
            "defaultVisible" to style.defaultVisible,
        )
    }
    val seizureGroupsJson = mapper.writeValueAsString(seizureWindowGroups)

    // Summary stats
    val totalSmall = rows.sumOf { it.smallSeizures }
    val totalBig = rows.sumOf { it.bigSeizures }

    val html = buildHtmlTemplate(
        labelsJson, seriesJson, drugLegendJson, seizureGroupsJson,
        legendSelectedJson, totalSmall, totalBig,
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
    totalSmall: Int,
    totalBig: Int,
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
        }

        #reportChart {
            width: 100%;
            height: 65vh;
            min-height: 400px;
            max-height: 700px;
        }
    </style>
</head>
<body>
    <!-- Top bar -->
    <div class="topbar">
        <div class="topbar-brand">
            <h1>Seizure Analyzer</h1>
            <span class="meta">${ANALYSIS_START} &ndash; ${ANALYSIS_END}</span>
        </div>
        <div class="stat">
            <strong>${totalSmall + totalBig}</strong> seizures
            <span style="color:#cbd5e1">&middot;</span>
            ${totalSmall} small
            <span style="color:#cbd5e1">&middot;</span>
            ${totalBig} big
        </div>
    </div>

    <!-- Controls -->
    <div class="controls" id="controls"></div>

    <!-- Chart -->
    <div class="chart-wrap">
        <div id="reportChart"></div>
    </div>

    <script>
        const labels = $labelsJson;
        const seriesData = $seriesJson;
        const drugNames = $drugLegendJson;
        const seizureGroups = $seizureGroupsJson;
        const defaultHidden = $legendSelectedJson;

        const allNames = seriesData.map(s => s.name);
        seizureGroups.forEach(g => {
            if (!allNames.includes(g.smallName)) allNames.push(g.smallName);
            if (!allNames.includes(g.bigName)) allNames.push(g.bigName);
        });

        const selected = {};
        allNames.forEach(n => { selected[n] = !(n in defaultHidden); });

        const chart = echarts.init(document.getElementById('reportChart'));

        const option = {
            backgroundColor: 'transparent',
            animationDuration: 300,
            legend: { show: false },
            tooltip: {
                trigger: 'axis',
                axisPointer: { type: 'cross', lineStyle: { color: '#cbd5e1', width: 1 }, crossStyle: { color: '#cbd5e1' }, label: { backgroundColor: '#64748b' } },
                backgroundColor: 'rgba(255,255,255,0.96)',
                borderColor: '#e2e8f0',
                borderWidth: 1,
                textStyle: { color: '#0f172a', fontSize: 12 },
                formatter: function(params) {
                    let html = '<div style="font-weight:600;margin-bottom:6px;font-size:13px">' + params[0].axisValue + '</div>';
                    params.forEach(p => {
                        if (p.value == null) return;
                        const dot = '<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:' + p.color + ';margin-right:6px;vertical-align:middle"></span>';
                        html += '<div style="display:flex;justify-content:space-between;gap:20px;font-size:12px;line-height:1.6">';
                        html += '<span>' + dot + p.seriesName + '</span>';
                        html += '<span style="font-weight:600;font-variant-numeric:tabular-nums">' + (typeof p.value === 'number' ? (p.value % 1 ? p.value.toFixed(2) : p.value) : p.value) + '</span>';
                        html += '</div>';
                    });
                    return html;
                }
            },
            grid: { left: 50, right: 50, top: 16, bottom: 32, containLabel: false },
            xAxis: {
                type: 'category',
                boundaryGap: false,
                data: labels,
                axisLabel: { color: '#94a3b8', fontSize: 11, hideOverlap: true },
                axisLine: { lineStyle: { color: '#e2e8f0' } },
                axisTick: { show: false }
            },
            yAxis: [
                {
                    type: 'value',
                    position: 'left',
                    axisLabel: { color: '#94a3b8', fontSize: 11, formatter: v => v.toFixed(1) },
                    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
                    min: 0, max: 1
                },
                {
                    type: 'value',
                    position: 'right',
                    axisLabel: { color: '#94a3b8', fontSize: 11 },
                    splitLine: { show: false },
                    minInterval: 1,
                    max: null
                }
            ],
            dataZoom: [
                { type: 'inside', throttle: 50 }
            ],
            series: seriesData
        };

        chart.setOption(option);

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
                chip.classList.toggle('off', !nowVisible);
                chart.dispatchAction({ type: 'legendToggleSelect', name: g.smallName });
                chart.dispatchAction({ type: 'legendToggleSelect', name: g.bigName });
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
                option.yAxis[1].max = (v === 'Auto') ? null : Number(v);
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
        });
        ctrl.appendChild(hideBtn);

        window.addEventListener('resize', () => chart.resize());
    </script>
</body>
</html>
""".trimIndent()
