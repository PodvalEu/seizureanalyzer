# Next Task: Implement Swim Lanes Drug Visualization

## Task
Integrate the swim lanes drug visualization into the Kotlin `HtmlReportWriter.kt` so the app generates it natively. Currently the lanes variant exists only as a standalone HTML file (`data/variant_lanes.html`) with hardcoded data. The goal is to make `buildHtmlTemplate()` produce the lanes layout with real data.

## What Needs to Change

### File: `src/main/kotlin/seizureanalyzer/output/HtmlReportWriter.kt`

**Kotlin data prep: KEEP AS-IS.** The normalized drug data, drug series, seizure series, and all JSON outputs remain unchanged. The template receives the same variables.

**`buildHtmlTemplate()` function: REPLACE** with the swim lanes layout.

### Key Differences from Current Template

The current template uses a single ECharts grid with dual Y-axes (left=drugs 0-1, right=seizures). The new lanes template needs:

1. **Multi-grid ECharts layout:**
   - One grid per drug (6 grids for 6 drugs), each 40px tall, stacked vertically from top
   - One main grid below all drug lanes for seizure curves
   - Each drug grid: `{ left: 80, right: 50, top: i*40, height: 36 }`
   - Main grid: `{ left: 80, right: 50, top: numDrugs*40+8, bottom: 32 }`

2. **X/Y axes per grid:**
   - Drug grids: hidden X axis (category, shared labels), hidden Y axis (value, 0-1)
   - Main grid: visible X axis (category, date labels), visible Y axis (value, right-positioned, seizure count)

3. **Series assignment:**
   - Drug series -> assigned to their respective grid via `xAxisIndex: i, yAxisIndex: i`
   - Drug rendering: `step: 'end'`, `areaStyle: { opacity: 0.25 }`, `lineStyle: { width: 1.5 }`
   - Seizure series -> assigned to main grid via `xAxisIndex: mainIdx, yAxisIndex: mainIdx`

4. **Graphic elements for labels:**
   - Drug name text labels: `{ type: 'text', left: 4, top: i*40 + 14, style: { text: name, fill: color, fontSize: 11, fontWeight: 600 } }`
   - Lane separator lines between drugs
   - Thicker separator line between drug lanes and seizure chart

5. **Linked dataZoom:**
   - `{ type: 'inside', xAxisIndex: [0,1,2,...,numDrugs], throttle: 50 }`
   - All grids zoom together

6. **Chart container height:**
   - CSS: `height: 75vh; min-height: 550px; max-height: 900px;`

### Template Variables Available (interpolated via Kotlin string template)
```
$labelsJson         — JSON array of date strings
$seriesJson         — JSON array of ECharts series configs (drugs + seizures)
$drugLegendJson     — JSON array of drug name strings
$seizureGroupsJson  — JSON array of {label, smallName, bigName, smallColor, bigColor, defaultVisible}
$legendSelectedJson — JSON object of {seriesName: false} for initially hidden
$totalSmall         — Int, total small seizures
$totalBig           — Int, total big seizures
${Config.analysisStart}  — "2024-01-01"
${Config.analysisEnd}    — "2026-01-15"
```

### Complete JS Logic for Lanes (from working variant_lanes.html)

```javascript
// Separate drug and seizure series from the combined seriesData
const drugSeries = seriesData.filter(s => drugNames.includes(s.name));
const seizureSeries = seriesData.filter(s => !drugNames.includes(s.name));
const drugColors = {};
const laneHeight = 40;
const numDrugs = drugNames.length;
const laneTotal = laneHeight * numDrugs;

const grids = [];
const xAxes = [];
const yAxes = [];
const allSeries = [];

// One grid per drug
drugSeries.forEach((ds, i) => {
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
seizureSeries.forEach(s => {
    allSeries.push({...s, xAxisIndex: mainXIdx, yAxisIndex: mainYIdx});
});

const axisIndices = xAxes.map((_, i) => i);

// ECharts option
const option = {
    backgroundColor: 'transparent', animationDuration: 300,
    legend: { show: false },
    tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'line', lineStyle: { color: '#cbd5e1', width: 1 }, label: { backgroundColor: '#64748b' } },
        backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e2e8f0', borderWidth: 1,
        textStyle: { color: '#0f172a', fontSize: 12 },
        formatter: function(params) {
            if (!params || !params.length) return '';
            let html = '<div style="font-weight:600;margin-bottom:6px;font-size:13px">' + params[0].axisValue + '</div>';
            params.forEach(p => {
                if (p.value == null) return;
                const dot = '<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:' + p.color + ';margin-right:6px;vertical-align:middle"></span>';
                html += '<div style="display:flex;justify-content:space-between;gap:20px;font-size:12px;line-height:1.6"><span>' + dot + p.seriesName + '</span><span style="font-weight:600;font-variant-numeric:tabular-nums">' + (typeof p.value === 'number' ? (p.value % 1 ? p.value.toFixed(2) : p.value) : p.value) + '</span></div>';
            });
            return html;
        }
    },
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
```

### Control Strip (unchanged from current design)
Drug chips, seizure group chips, scale presets (Auto/10/20/30/50), range presets (All/1y/180d/90d), show/hide all. The only difference: scale buttons must reference `option.yAxis[mainYIdx].max` instead of `option.yAxis[1].max`.

### CSS Changes
Only the chart container height changes:
```css
#reportChart { height: 75vh; min-height: 550px; max-height: 900px; }
```

## How to Build & Test
```bash
cd .
./gradlew --no-daemon installDist
./build/install/seizureanalyzer/bin/seizureanalyzer
```
Then preview `data/report-N.html` (N is auto-incremented).

## Known Issues to Watch For
- Drug lane data may appear sparse for drugs active only briefly (Diazepam, Fycompa) — correct behavior
- `graphic` elements use absolute pixel positions — ECharts handles this gracefully
- Tooltip shows normalized 0-1 drug values; consider showing "active"/"inactive" instead
- Scale buttons must reference `mainYIdx` (dynamic index) not hardcoded `1`
