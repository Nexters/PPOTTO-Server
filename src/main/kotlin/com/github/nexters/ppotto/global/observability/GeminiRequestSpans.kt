package com.github.nexters.ppotto.global.observability

import com.google.genai.types.Content
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
        ?.takeIf { it.isNotBlank() }
        ?.let(::setSystemInstructions)
    content
        .toLlmMessage(LlmRole.USER)
        ?.let { setInputMessages(listOf(it)) }
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

private fun Part.toLlmMessagePart(): LlmMessagePart? =
    text()
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?.let(LlmMessagePart::Text)
        ?: fileData()
            .getOrNull()
            ?.let { fileData ->
                fileData
                    .fileUri()
                    .getOrNull()
                    ?.let { uri ->
                        LlmMessagePart.Uri(
                            uri = uri,
                            mimeType =
                                fileData
                                    .mimeType()
                                    .getOrNull()
                                    .orEmpty(),
                        )
                    }
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
