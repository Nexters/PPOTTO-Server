package com.github.nexters.ppotto.global.observability

internal class RecordingLlmSpanHandle : LlmSpanHandle {
    var usageRecorded: Boolean = false
    var inputTokens: Int? = null
    var outputTokens: Int? = null
    var cachedInputTokens: Int? = null
    var reasoningOutputTokens: Int? = null
    var totalTokens: Int? = null
    var recordedResponseModel: String? = null
    var recordedResponseId: String? = null
    var recordedFinishReasons: List<String>? = null
    var recordedInputMessages: List<LlmMessage>? = null
    var recordedOutputMessages: List<LlmMessage>? = null
    var recordedSystemInstructions: String? = null
    val attributes: MutableMap<String, Any> = mutableMapOf()

    override fun setUsage(
        inputTokens: Int?,
        outputTokens: Int?,
        cachedInputTokens: Int?,
        reasoningOutputTokens: Int?,
        totalTokens: Int?,
    ) {
        usageRecorded = true
        this.inputTokens = inputTokens
        this.outputTokens = outputTokens
        this.cachedInputTokens = cachedInputTokens
        this.reasoningOutputTokens = reasoningOutputTokens
        this.totalTokens = totalTokens
    }

    override fun setResponseModel(model: String) {
        recordedResponseModel = model
    }

    override fun setResponseId(responseId: String) {
        recordedResponseId = responseId
    }

    override fun setFinishReasons(reasons: List<String>) {
        recordedFinishReasons = reasons
    }

    override fun setInputMessages(messages: List<LlmMessage>) {
        recordedInputMessages = messages
    }

    override fun setOutputMessages(messages: List<LlmMessage>) {
        recordedOutputMessages = messages
    }

    override fun setSystemInstructions(instructions: String) {
        recordedSystemInstructions = instructions
    }

    override fun setAttribute(
        key: String,
        value: String,
    ) {
        attributes[key] = value
    }

    override fun setAttribute(
        key: String,
        value: Long,
    ) {
        attributes[key] = value
    }
}
