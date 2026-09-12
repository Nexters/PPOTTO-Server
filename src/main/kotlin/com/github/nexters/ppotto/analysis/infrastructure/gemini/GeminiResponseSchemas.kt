package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.google.genai.types.Schema

internal object GeminiResponseSchemas {
    val STICKER_RESPONSE_SCHEMA: Schema = geminiSchema<GeminiStickerRegenerationResponse>()

    val VERIFICATION_RESPONSE_SCHEMA: Schema = geminiSchema<GeminiSubjectVerificationResponse>()

    val CLASSIFICATION_RESPONSE_SCHEMA: Schema =
        geminiListSchema<GeminiThemeResponse>(
            description = "One entry per theme, in the order you want them shown.",
            minItems = ThemeClassificationValidator.MIN_THEME_COUNT,
            maxItems = ThemeClassificationValidator.MAX_THEME_COUNT,
        )
}
