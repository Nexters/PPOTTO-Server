package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator

internal fun themeClassificationPrompt(photoAliases: List<String>): GeminiPrompt =
    geminiPrompt(systemInstruction = COPY_VOICE) {
        section(
            promptSection(
                "theme-classification/task",
                "minThemeCount" to ThemeClassificationValidator.MIN_THEME_COUNT,
                "maxThemeCount" to ThemeClassificationValidator.MAX_THEME_COUNT,
            ),
        )
        section(photoAliasSection(photoAliases))
        section(promptSection("theme-classification/field-order"))
        section(STICKER_CANDIDATE_GUIDE)
        section(COPY_STYLE_EXAMPLES)
        section(OUTPUT_LANGUAGE)
    }
