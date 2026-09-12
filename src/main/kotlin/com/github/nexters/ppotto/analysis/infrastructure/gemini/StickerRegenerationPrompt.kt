package com.github.nexters.ppotto.analysis.infrastructure.gemini

internal fun stickerRegenerationPrompt(
    photoAliases: List<String>,
    previousSourcePhotoAlias: String?,
): GeminiPrompt =
    geminiPrompt {
        section(TASK)
        section(
            "photo aliases",
            """
            ${photoAliasSection(photoAliases).text}
            Photo alias previously used as the sticker source: ${previousSourcePhotoAlias ?: "not available"} (pick
            a different photo or subject if possible)
            """.trimIndent(),
        )
        section(FIELDS)
        section(STICKER_CANDIDATE_GUIDE)
        section(OUTPUT_LANGUAGE)
    }

private val TASK =
    PromptSection(
        "task",
        """
        The photos attached below are already classified under the same theme. Don't change this set of photos —
        just pick a new subject and source photo to turn into a sticker from among them.
        """.trimIndent(),
    )

private val FIELDS =
    PromptSection(
        "fields",
        """
        Generate the following:
        - sourcePhotoId: FIRST, before writing any subject description, pick the photo alias to use as the
          sticker source. It must be a value that appears in the alias list above.
        - targetSubject: ONLY AFTER you have picked sourcePhotoId above, look again at that exact photo and
          write a specific description (in Korean) of a subject that is literally, visibly present in that exact
          photo. Do not describe something you recall from a different photo in this batch, and do not write an
          idealized or generic subject — describe only what is actually depicted in the sourcePhotoId photo you
          just chose. Before finalizing, re-check yourself: if you looked at that sourcePhotoId photo again right
          now, would everything in targetSubject be immediately visible in it? If not, either choose a different
          sourcePhotoId or rewrite targetSubject to match what that photo truly shows.
        - mainColor: the single most representative color of that subject as it actually appears in the source
          photo, as a 6-digit hex code (e.g. "#FF6B6B"). Pick the color a viewer would call "the color of this
          thing", not a shadow, highlight, or background color.
        """.trimIndent(),
    )
