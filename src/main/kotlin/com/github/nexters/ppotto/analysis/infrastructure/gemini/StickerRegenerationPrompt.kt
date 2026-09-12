package com.github.nexters.ppotto.analysis.infrastructure.gemini

internal fun stickerRegenerationPrompt(
    photoAliases: List<String>,
    previousSourcePhotoAlias: String?,
): GeminiPrompt =
    geminiPrompt {
        section(promptSection("sticker-regeneration/task"))
        section(
            promptSection(
                "sticker-regeneration/photo-aliases",
                "photoAliases" to photoAliases.joinToString(", "),
                "previousSourcePhotoAlias" to (previousSourcePhotoAlias ?: "not available"),
            ),
        )
        section(promptSection("sticker-regeneration/fields"))
        section(STICKER_CANDIDATE_GUIDE)
        section(OUTPUT_LANGUAGE)
    }
