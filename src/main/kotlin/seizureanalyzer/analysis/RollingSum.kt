package seizureanalyzer.analysis

import seizureanalyzer.model.DailyRow
import kotlin.math.min

internal fun applyForwardRolling(rows: List<DailyRow>, windows: List<Int>) {
    windows.forEach { window ->
        val smallPrefix = IntArray(rows.size + 1)
        val bigPrefix = IntArray(rows.size + 1)
        rows.forEachIndexed { index, row ->
            smallPrefix[index + 1] = smallPrefix[index] + row.smallSeizures
            bigPrefix[index + 1] = bigPrefix[index] + row.bigSeizures
        }

        rows.forEachIndexed { index, row ->
            val endExclusive = min(rows.size, index + window)
            val smallSum = smallPrefix[endExclusive] - smallPrefix[index]
            val bigSum = bigPrefix[endExclusive] - bigPrefix[index]
            row.setForwardSum(window, smallSum, bigSum)
        }
    }
}
