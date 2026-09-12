package com.github.nexters.ppotto.analysis.infrastructure.gemini

internal fun stickerSubjectVerificationPrompt(targetSubject: String): GeminiPrompt =
    geminiPrompt {
        section(promptSection("sticker-subject-verification/task", "targetSubject" to targetSubject))
        section(promptSection("sticker-subject-verification/fields"))
        section(STICKER_CANDIDATE_GUIDE)
        section(OUTPUT_LANGUAGE)
    }
