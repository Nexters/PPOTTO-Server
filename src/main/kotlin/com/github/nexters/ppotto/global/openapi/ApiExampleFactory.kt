package com.github.nexters.ppotto.global.openapi

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.examples.Example
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiExampleFactory(
    private val objectMapper: ObjectMapper,
) {
    fun create(example: ApiExample): Example =
        Example()
            .description(example.name)
            .summary(example.summary)
            .value(render(example.value))

    fun createUnnamed(value: Any): Example = Example().value(render(value))

    private fun render(value: Any): Any = Json.mapper().readTree(objectMapper.writeValueAsString(value))
}
