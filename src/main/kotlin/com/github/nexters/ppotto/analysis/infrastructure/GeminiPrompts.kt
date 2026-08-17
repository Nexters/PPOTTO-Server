package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator

object GeminiPrompts {
    fun themeClassification(photoAliases: List<String>): String =
        listOf(
            """
            Classify the attached photos into at most ${ThemeClassificationValidator.MAX_THEME_COUNT} themes. Each photo must belong to exactly one theme,
            and you may exclude photos that don't fit any theme from the result.
            """.trimIndent(),
            "Photo alias list (in the same order as the attached photos): ${photoAliases.joinToString(", ")}",
            """
            For each theme, generate:
            - theme: theme name (in Korean)
            - categorizedPhotoIds: the list of photo aliases classified under this theme (use only values from the alias list above)
            - recap.badge: a short badge phrase, around 8 characters (in Korean), always ending with exactly one emoji that fits the theme's mood (e.g. "여행 필수템 🧳")
            - recap.text: a single complete, natural-sounding sentence (in Korean), aiming for around 20 Korean characters or fewer so it reads well on a narrow mobile screen. Never truncate mid-sentence or force an unnatural cut just to hit the length target — write it so it is naturally short instead.
            - sticker.sourcePhotoId: FIRST, before writing any subject description, pick the photo alias to use as the sticker source. It must be a value that actually appears in **this theme's own categorizedPhotoIds array**. Never use an alias that exists in the overall photo list but is NOT in this theme's categorizedPhotoIds (i.e., an alias belonging to a different theme) — always copy one of the aliases you just listed in categorizedPhotoIds above.
            - sticker.targetSubject: ONLY AFTER you have picked sourcePhotoId above, look again at that exact photo and write a specific description (in Korean) of a subject that is literally, visibly present in that exact photo. Do not describe something you recall from a different photo in this batch, and do not write an idealized or generic subject — describe only what is actually depicted in the sourcePhotoId photo you just chose. Before finalizing, re-check yourself: if you looked at that sourcePhotoId photo again right now, would everything in targetSubject be immediately visible in it? If not, either choose a different sourcePhotoId or rewrite targetSubject to match what that photo truly shows.
            - sticker.mainColor: the single most representative color of that subject as it actually appears in the source photo, as a 6-digit hex code (e.g. "#FF6B6B"). Pick the color a viewer would call "the color of this thing" — usually its dominant surface/body color, not a shadow, highlight, or background color.
            - comments.speechBubbles: 2-4 short reaction phrases (in Korean) that float around the sticker like speech bubbles, each with:
              - content: a short, punchy reaction to this theme's photos (in Korean)
              - posX / posY: a relative offset in pixels from the sticker's center, roughly between -150 and 150, chosen so the bubbles scatter naturally around the sticker without overlapping each other
            - comments.keywordChips: 4-8 short keywords or phrases (in Korean) that summarize this theme, shown as chips below the sticker — no position needed
            All of recap.text, comments.speechBubbles, and comments.keywordChips should read as one consistent voice about the same theme — keep their tone and context flowing naturally from one another instead of feeling like disconnected fragments.
            """.trimIndent(),
            STICKER_CANDIDATE_GUIDE,
            OUTPUT_LANGUAGE_NOTE,
        ).joinToString("\n\n")

    fun stickerRegeneration(
        photoAliases: List<String>,
        previousSourcePhotoAlias: String?,
    ): String =
        listOf(
            """
            The photos attached below are already classified under the same theme. Don't change this set of photos —
            just pick a new subject and source photo to turn into a sticker from among them.
            """.trimIndent(),
            """
            Photo alias list (in the same order as the attached photos): ${photoAliases.joinToString(", ")}
            Photo alias previously used as the sticker source: ${previousSourcePhotoAlias ?: "not available"} (pick a different photo or subject if possible)
            """.trimIndent(),
            """
            Generate the following:
            - sourcePhotoId: FIRST, before writing any subject description, pick the photo alias to use as the sticker source. It must be a value that appears in the alias list above.
            - targetSubject: ONLY AFTER you have picked sourcePhotoId above, look again at that exact photo and write a specific description (in Korean) of a subject that is literally, visibly present in that exact photo. Do not describe something you recall from a different photo in this batch, and do not write an idealized or generic subject — describe only what is actually depicted in the sourcePhotoId photo you just chose. Before finalizing, re-check yourself: if you looked at that sourcePhotoId photo again right now, would everything in targetSubject be immediately visible in it? If not, either choose a different sourcePhotoId or rewrite targetSubject to match what that photo truly shows.
            - mainColor: the single most representative color of that subject as it actually appears in the source photo, as a 6-digit hex code (e.g. "#FF6B6B"). Pick the color a viewer would call "the color of this thing", not a shadow, highlight, or background color.
            """.trimIndent(),
            STICKER_CANDIDATE_GUIDE,
            OUTPUT_LANGUAGE_NOTE,
        ).joinToString("\n\n")

    fun verifyStickerSubject(targetSubject: String): String =
        listOf(
            """
            A subject was chosen to turn into a sticker from a different photo-selection step, and was described (in Korean) as: '$targetSubject'. That earlier step could not see this photo in isolation, so it may have gotten the description wrong or confused it with a different photo. Only the single photo attached below is available now — treat it as the only source of truth.

            First, check whether everything in that description is literally, visibly present in this exact attached photo.
            - If yes, confirm it as-is.
            - If no, or only partially, rewrite the description (in Korean) to describe a specific, real, independently-isolable subject that is actually, visibly present in this exact photo — do not keep any part of the original description that isn't really here.
            - If nothing in this photo is remotely appealing or isolable as a sticker subject, say so explicitly rather than forcing a description.
            """.trimIndent(),
            """
            Output:
            - subjectPresent: true if you found a valid subject in this photo (whether it matched the original description or you had to rewrite it), false if nothing usable is in this photo
            - targetSubject: the confirmed or corrected subject description (in Korean); required only if subjectPresent is true
            - mainColor: the single most representative color of that subject as it actually appears in this photo, as a 6-digit hex code (e.g. "#FF6B6B"); required only if subjectPresent is true
            """.trimIndent(),
            STICKER_CANDIDATE_GUIDE,
            OUTPUT_LANGUAGE_NOTE,
        ).joinToString("\n\n")

    private val STICKER_CANDIDATE_GUIDE =
        """
        Pick a sticker source photo and subject that a user would intuitively find pretty, cool, cute, or impressive.
        Criteria for a good sticker candidate:
        - The subject is clear and large enough, has good lighting and color, and has an appealing composition or pose
        - The subject's silhouette and meaning survive well as an independent element even with the background removed
        - It symbolically represents the theme well, with clear emotion or personality
        Avoid:
        - Describing a targetSubject that is not actually, literally visible in the sourcePhotoId photo you chose — even a plausible-sounding or thematically fitting description is a critical failure if it's not really in that photo, because the downstream cutout step will have nothing to isolate and will fail
        - Subjects that are blurry, dark, or too small, so they'd look poor after cutout
        - Subjects where multiple objects overlap in a complex way with ambiguous boundaries
        - Scenes where the background itself is essential, so removing it would weaken the meaning
        - Lumping the entire captured scene (an entire table spread, an entire group of people, or any wide area that isn't actually a landscape) into a single "subject" — for example, describing it as "an entire table full of various foods" means the cutout would end up looking almost identical to the original photo
        - Picking a sourcePhotoId photo that itself doesn't look like an actual camera photo — e.g. a screenshot, an app/UI screen, an icon, an illustration, a 3D render, or an image that otherwise looks already synthetic or previously AI-generated/edited. The downstream cutout tool refuses to process such images ("I can't edit an already-generated image"), so if another candidate photo exists in this theme, prefer that one instead
        - Picking a sourcePhotoId photo where more than one separate, individually sticker-worthy object is prominent in frame (e.g. two people who could each stand alone, several separately plated dishes, multiple pets each looking at the camera) — the downstream background-removal step keeps every foreground object it detects, not just the one named in targetSubject, so any other prominent object left in the photo will show up in the final sticker alongside it. Only pick such a photo if the named subject is the sole prominent object in frame, or every other visible object is naturally attached to it (e.g. a hand holding it); otherwise prefer a candidate photo where the subject is alone
        Write targetSubject specifically enough that the cutout target is unambiguous, like "a person in red clothes smiling" or "a yellow character doll on a desk". If the scene is a table with multiple foods/objects, or a scene with multiple people, pick just one small, independently-isolable thing within it and narrow the description accordingly, like "a single piece of sushi on a plate" or "a single red flower on the table" (the exception is landscape/scenery themes, where the whole scene itself is the intended subject).
        """.trimIndent()

    private const val OUTPUT_LANGUAGE_NOTE = "Write all text output in Korean."
}
