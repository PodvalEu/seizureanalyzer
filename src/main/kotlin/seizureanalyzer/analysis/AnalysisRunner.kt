package seizureanalyzer.analysis

import seizureanalyzer.model.AnalysisResults
import seizureanalyzer.model.CategorizedEvents
import seizureanalyzer.model.DailyRow

internal fun runAnalysis(rows: List<DailyRow>, categorized: CategorizedEvents): AnalysisResults =
    AnalysisResults(
        drugChangeImpacts = analyzeDrugImpacts(rows, categorized),
        regimenRanking = analyzeRegimens(rows),
        monthlyTrend = computeMonthlyTrend(rows),
        seizureFreeStreaks = analyzeStreaks(rows),
        drugCorrelations = computeDrugCorrelations(rows),
        lagCorrelations = computeLagCorrelations(rows),
    )
