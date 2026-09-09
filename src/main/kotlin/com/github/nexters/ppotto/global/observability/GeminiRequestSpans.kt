package com.github.nexters.ppotto.global.observability

import com.google.genai.types.Content
import com.google.genai.types.FileData
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import kotlin.jvm.optionals.getOrNull

fun LlmSpanHandle.recordRequest(
    content: Content,
    config: GenerateContentConfig?,
) {
    config
        ?.systemInstruction()
        ?.getOrNull()
        ?.let(::systemInstructionText)
        ?.takeIf(String::isNotBlank)
        ?.let(::setSystemInstructions)

    content
        .toLlmMessage(LlmRole.USER)
        ?.let { message -> setInputMessages(listOf(message)) }
}

internal fun Content.toLlmMessage(defaultRole: LlmRole): LlmMessage? =
    parts()
        .getOrNull()
        ?.mapNotNull(Part::toLlmMessagePart)
        ?.takeIf { it.isNotEmpty() }
        ?.let { parts -> LlmMessage(role = resolveRole(defaultRole), parts = parts) }

private fun Content.resolveRole(defaultRole: LlmRole): LlmRole =
    role()
        .getOrNull()
        ?.let { role ->
            when (role.lowercase()) {
                MODEL_ROLE, LlmRole.ASSISTANT.value -> LlmRole.ASSISTANT
                LlmRole.USER.value -> LlmRole.USER
                else -> defaultRole
            }
        } ?: defaultRole

private fun Part.toLlmMessagePart(): LlmMessagePart? {
    val text = text().getOrNull()
    if (!text.isNullOrBlank()) return LlmMessagePart.Text(text)
    return fileData().getOrNull()?.toUriPart()
}

private fun FileData.toUriPart(): LlmMessagePart.Uri? {
    val uri = fileUri().getOrNull() ?: return null
    return LlmMessagePart.Uri(
        uri = uri,
        mimeType =
            mimeType()
                .getOrNull()
                .orEmpty(),
    )
}

private fun systemInstructionText(instruction: Content): String =
    instruction
        .parts()
        .getOrNull()
        ?.mapNotNull { it.text().getOrNull() }
        ?.joinToString(SYSTEM_INSTRUCTION_SEPARATOR)
        .orEmpty()

private const val MODEL_ROLE = "model"
private const val SYSTEM_INSTRUCTION_SEPARATOR = "\n"
