package com.github.nexters.ppotto.global.openapi

data class ApiExample(
    val name: String,
    val summary: String? = null,
    val value: Any,
)

data class OperationExamples(
    val request: List<ApiExample> = emptyList(),
    val responses: Map<String, List<ApiExample>> = emptyMap(),
)
