package com.github.nexters.ppotto.global.observability

enum class LlmPipeline(
    val value: String,
) {
    PHOTO_CLASSIFICATION("photo-classification"),
    STICKER_REGENERATION("sticker-regeneration"),
    STICKER_SUBJECT_VERIFICATION("sticker-subject-verification"),
}

enum class LlmRole(
    val value: String,
) {
    USER("user"),
    ASSISTANT("assistant"),
}

sealed interface LlmMessagePart {
    data class Text(
        val content: String,
    ) : LlmMessagePart

    data class Uri(
        val uri: String,
        val mimeType: String,
    ) : LlmMessagePart
}

data class LlmMessage(
    val role: LlmRole,
    val parts: List<LlmMessagePart>,
)

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

    fun setInputMessages(messages: List<LlmMessage>)

    fun setOutputMessages(messages: List<LlmMessage>)

    fun setSystemInstructions(instructions: String)

    fun setAttribute(
        key: String,
        value: String,
    )

    fun setAttribute(
        key: String,
        value: Long,
    )
}
