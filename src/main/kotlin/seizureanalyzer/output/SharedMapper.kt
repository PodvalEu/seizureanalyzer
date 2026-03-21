package seizureanalyzer.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

internal val JACKSON_MAPPER: ObjectMapper = ObjectMapper().registerModule(kotlinModule())
