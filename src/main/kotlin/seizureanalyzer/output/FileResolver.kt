package seizureanalyzer.output

import java.io.File

internal fun resolveReportHtmlFile(baseFile: File): File {
    val parent = baseFile.parentFile ?: File("/data")
    parent.mkdirs()

    val name = baseFile.nameWithoutExtension
    val extension = baseFile.extension.ifEmpty { "html" }

    if ("{runId}" in baseFile.name || "*" in baseFile.name || ":timestamp:" in baseFile.name) {
        val runId = System.currentTimeMillis()
        val dynamicName = baseFile.name
            .replace("{runId}", runId.toString())
            .replace(":timestamp:", runId.toString())
            .replace("*", runId.toString())
        return File(parent, dynamicName)
    }

    val existingIds = parent.listFiles()?.mapNotNull { file ->
        val regex = Regex("^${Regex.escape(name)}-(\\d+)\\.${Regex.escape(extension)}").matchEntire(file.name)
        regex?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.orEmpty()
    val nextId = (existingIds.maxOrNull() ?: 0) + 1
    return File(parent, "$name-$nextId.$extension")
}

internal fun resolveEventsJsonOut(template: String, runId: Long): String = when {
    template.contains("{runId}") -> template.replace("{runId}", runId.toString())
    template.contains(":timestamp:") -> template.replace(":timestamp:", runId.toString())
    template.contains("*") -> template.replace("*", runId.toString())
    else -> template
}
