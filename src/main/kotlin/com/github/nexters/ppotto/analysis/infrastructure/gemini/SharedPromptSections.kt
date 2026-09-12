package com.github.nexters.ppotto.analysis.infrastructure.gemini

internal val STICKER_CANDIDATE_GUIDE = promptSection("shared/sticker-candidate-guide")

internal val COPY_STYLE_EXAMPLES = promptSection("shared/copy-style-examples")

internal val OUTPUT_LANGUAGE = promptSection("shared/output-language")

internal val COPY_VOICE = promptSection("shared/copy-voice").text

internal fun photoAliasSection(photoAliases: List<String>): PromptSection =
    promptSection("shared/photo-aliases", "photoAliases" to photoAliases.joinToString(", "))
