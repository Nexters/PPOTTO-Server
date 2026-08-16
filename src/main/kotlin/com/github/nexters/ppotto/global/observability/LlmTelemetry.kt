package com.github.nexters.ppotto.global.observability

enum class LlmPipeline(
    val value: String,
) {
    PHOTO_CLASSIFICATION("photo-classification"),
    STICKER_REGENERATION("sticker-regeneration"),
    STICKER_SUBJECT_VERIFICATION("sticker-subject-verification"),
}

interface LlmSpanHandle {
    fun setUsage(
        inputTokens: Int?,
        outputTokens: Int?,
        cachedInputTokens: Int? = null,
        reasoningOutputTokens: Int? = null,
        totalTokens: Int? = null,
    )

    fun setResponseModel(model: String)

    fun setResponseId(responseId: String)

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
