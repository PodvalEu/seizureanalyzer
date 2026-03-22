package seizureanalyzer.parsing

import seizureanalyzer.model.TimeSource

internal data class ExtractedTime(val hour: Int?, val source: TimeSource)

private val EXPLICIT_AMPM = Regex("""(\d{1,2})(:\d{2})?\s*(am|pm)""", RegexOption.IGNORE_CASE)
private val COMPACT_AMPM = Regex("""(\d{3,4})(am|pm)""", RegexOption.IGNORE_CASE)
private val TYPO_AM = Regex("""(\d{1,2}):(\d{2})\s*(an|aˇ)""", RegexOption.IGNORE_CASE)
private val BARE_COLON = Regex("""(\d{1,2}):(\d{2})(?!\s*(am|pm|an|aˇ))""", RegexOption.IGNORE_CASE)
private val CZECH_BARE_HOUR = Regex("""(?<!\d)\bv[e ]?\s*(\d{1,2})\s*h?\b""", RegexOption.IGNORE_CASE)
private val BARE_TRAILING_HOUR = Regex("""\b(\d{1,2})\s*$""")
private val BARE_COMPACT = Regex("""\b(\d{3,4})\b""")

private val CZECH_MORNING = Regex("""(^|\W)(rano|ráno|při vstávání|pri vstavani|po probuzeni|po probuzení|po probudezení)(\W|$)""", RegexOption.IGNORE_CASE)
private val CZECH_FORENOON = Regex("""(^|\W)(dopoledne|dopol)(\W|$)""", RegexOption.IGNORE_CASE)
private val CZECH_NOON = Regex("""(^|\W)v poledne(\W|$)""", RegexOption.IGNORE_CASE)
private val CZECH_AFTERNOON = Regex("""(^|\W)(odpoledne|odpopedne|odpol)(\W|$)""", RegexOption.IGNORE_CASE)
private val CZECH_EVENING = Regex("""(^|\W)(vecer|večer)(\W|$)""", RegexOption.IGNORE_CASE)
private val CZECH_NIGHT = Regex("""(^|\W)(v noci|spánek|spanek)(\W|$)""", RegexOption.IGNORE_CASE)

private val LOC_BED = Regex("""(^|\W)v posteli(\W|$)""", RegexOption.IGNORE_CASE)
private val LOC_COMMUTE = Regex("""(^|\W)(pred skolou|před školou|u školy|u skoly|na zastavce|na zastávce|na metru|u šatny|u satny|do školy|do skoly|na ranni bus|v tramvaji|v tram\b|v aute)(\W|$)""", RegexOption.IGNORE_CASE)
private val LOC_SCHOOL = Regex("""(^|\W)(ve škole|ve skole|v družině|v druzine|v šatně|v satne|asistentk|ze školy|ze skoly)(\W|$)""", RegexOption.IGNORE_CASE)
private val LOC_AFTERNOON_PLACE = Regex("""(^|\W)(v bazénu|v bazenu|na plavání|na plavani|v skateparku)(\W|$)""", RegexOption.IGNORE_CASE)
private val LOC_ELEVATOR = Regex("""(^|\W)ve výtahu(\W|$)""", RegexOption.IGNORE_CASE)

internal fun extractHour(summary: String): ExtractedTime {
    // 1. Explicit am/pm: "8am", "8:30am", "7pm"
    EXPLICIT_AMPM.find(summary)?.let { m ->
        val h = m.groupValues[1].toInt()
        val ampm = m.groupValues[3].lowercase()
        val hour = toHour24(h, ampm)
        if (hour in 0..23) return ExtractedTime(hour, TimeSource.EXPLICIT_TIME)
    }

    // 2. Compact am/pm: "730am", "1030am"
    COMPACT_AMPM.find(summary)?.let { m ->
        val digits = m.groupValues[1]
        val ampm = m.groupValues[2].lowercase()
        val (h, min) = splitCompact(digits) ?: return@let
        if (min in 0..59) {
            val hour = toHour24(h, ampm)
            if (hour in 0..23) return ExtractedTime(hour, TimeSource.EXPLICIT_TIME)
        }
    }

    // 3. am typos: "10:00an", "9:30aˇ"
    TYPO_AM.find(summary)?.let { m ->
        val h = m.groupValues[1].toInt()
        if (h in 0..23) return ExtractedTime(h, TimeSource.EXPLICIT_TIME)
    }

    // 4. Bare H:MM colon without am/pm: "9:45" → treat as-is (AM for ≤12)
    BARE_COLON.find(summary)?.let { m ->
        val h = m.groupValues[1].toInt()
        if (h in 0..23) return ExtractedTime(h, TimeSource.EXPLICIT_TIME)
    }

    // 5. Czech bare hour: "v 8", "ve 13", "ve 14h"
    CZECH_BARE_HOUR.find(summary)?.let { m ->
        val h = m.groupValues[1].toInt()
        if (h in 0..23) return ExtractedTime(h, TimeSource.EXPLICIT_TIME)
    }

    // 6. Bare compact 3-4 digits: "630", "830", "1015"
    BARE_COMPACT.find(summary)?.let { m ->
        val digits = m.groupValues[1]
        val (h, min) = splitCompact(digits) ?: return@let
        if (h in 0..23 && min in 0..59) return ExtractedTime(h, TimeSource.EXPLICIT_TIME)
    }

    // 7. Bare trailing hour: "Staceni oci 9", "Záškuby noha 7"
    BARE_TRAILING_HOUR.find(summary)?.let { m ->
        val h = m.groupValues[1].toInt()
        if (h in 5..23) return ExtractedTime(h, TimeSource.EXPLICIT_TIME)
    }

    // 8. Czech time-of-day keywords
    if (CZECH_MORNING.containsMatchIn(summary)) return ExtractedTime(7, TimeSource.CZECH_KEYWORD)
    if (CZECH_FORENOON.containsMatchIn(summary)) return ExtractedTime(10, TimeSource.CZECH_KEYWORD)
    if (CZECH_NOON.containsMatchIn(summary)) return ExtractedTime(12, TimeSource.CZECH_KEYWORD)
    if (CZECH_AFTERNOON.containsMatchIn(summary)) return ExtractedTime(14, TimeSource.CZECH_KEYWORD)
    if (CZECH_EVENING.containsMatchIn(summary)) return ExtractedTime(20, TimeSource.CZECH_KEYWORD)
    if (CZECH_NIGHT.containsMatchIn(summary)) return ExtractedTime(3, TimeSource.CZECH_KEYWORD)

    // 9. Location inference
    if (LOC_BED.containsMatchIn(summary)) return ExtractedTime(7, TimeSource.LOCATION_INFERRED)
    if (LOC_COMMUTE.containsMatchIn(summary)) return ExtractedTime(8, TimeSource.LOCATION_INFERRED)
    if (LOC_SCHOOL.containsMatchIn(summary)) return ExtractedTime(10, TimeSource.LOCATION_INFERRED)
    if (LOC_AFTERNOON_PLACE.containsMatchIn(summary)) return ExtractedTime(15, TimeSource.LOCATION_INFERRED)
    if (LOC_ELEVATOR.containsMatchIn(summary)) return ExtractedTime(8, TimeSource.LOCATION_INFERRED)

    return ExtractedTime(null, TimeSource.UNKNOWN)
}

private fun toHour24(h: Int, ampm: String): Int = when {
    ampm == "am" && h == 12 -> 0
    ampm == "am" -> h
    ampm == "pm" && h == 12 -> 12
    ampm == "pm" -> h + 12
    else -> h
}

private fun splitCompact(digits: String): Pair<Int, Int>? {
    return if (digits.length == 3) {
        // "630" → 6:30, "815" → 8:15
        digits[0].digitToInt() to digits.substring(1).toInt()
    } else if (digits.length == 4) {
        // "1030" → 10:30
        digits.substring(0, 2).toInt() to digits.substring(2).toInt()
    } else null
}
