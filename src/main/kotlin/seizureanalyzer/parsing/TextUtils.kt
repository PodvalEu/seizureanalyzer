package seizureanalyzer.parsing

internal fun String.toDoubleValue(): Double = this.replace(',', '.').toDouble()

internal fun formatNumber(value: Double?): String = when {
    value == null -> ""
    value == value.toInt().toDouble() -> value.toInt().toString()
    else -> String.format("%.2f", value)
}

internal fun slugify(input: String): String =
    normalizeDrugName(input)
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
