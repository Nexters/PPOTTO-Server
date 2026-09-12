package com.github.nexters.ppotto.analysis.infrastructure.gemini

internal fun stickerSubjectVerificationPrompt(targetSubject: String): GeminiPrompt =
    geminiPrompt {
        section("task", verificationTask(targetSubject))
        section(FIELDS)
        section(STICKER_CANDIDATE_GUIDE)
        section(OUTPUT_LANGUAGE)
    }

private fun verificationTask(targetSubject: String): String =
    """
    A subject was chosen to turn into a sticker from a different photo-selection step, and was described (in
    Korean) as: '$targetSubject'. That earlier step could not see this photo in isolation, so it may have
    gotten the description wrong or confused it with a different photo. Only the single photo attached below
    is available now — treat it as the only source of truth.

    First, check whether everything in that description is literally, visibly present in this exact attached
    photo.
    - If yes, confirm it as-is.
    - If no, or only partially, rewrite the description (in Korean) to describe a specific, real,
      independently-isolable subject that is actually, visibly present in this exact photo — do not keep any
      part of the original description that isn't really here.
    - If nothing in this photo is remotely appealing or isolable as a sticker subject, say so explicitly
      rather than forcing a description.
    """.trimIndent()

private val FIELDS =
    PromptSection(
        "fields",
        """
        Output:
        - subjectPresent: true if you found a valid subject in this photo (whether it matched the original
          description or you had to rewrite it), false if nothing usable is in this photo
        - targetSubject: the confirmed or corrected subject description (in Korean); required only if
          subjectPresent is true
        - mainColor: the single most representative color of that subject as it actually appears in this photo,
          as a 6-digit hex code (e.g. "#FF6B6B"); required only if subjectPresent is true
        """.trimIndent(),
    )
