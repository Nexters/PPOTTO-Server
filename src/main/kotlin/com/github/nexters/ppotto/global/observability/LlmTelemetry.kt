package com.github.nexters.ppotto.global.observability

enum class LlmOperation(
    val value: String,
) {
    CLASSIFY("classify"),
    STICKER_REGENERATION("sticker_regeneration"),
    SUBJECT_VERIFICATION("subject_verification"),
}

interface LlmSpanHandle {
    fun setUsage(
        inputTokens: Int?,
        outputTokens: Int?,
        cachedTokens: Int? = null,
        totalTokens: Int? = null,
    )

    fun setResponseModel(model: String)

    fun setFinishReasons(reasons: List<String>)

    fun setAttribute(
        key: String,
        value: String,
    )

    fun setAttribute(
        key: String,
        value: Long,
    )
}
