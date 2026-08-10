package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator

object GeminiPrompts {
    fun stickerCutout(targetSubject: String): String =
        """
        Perform a precise background removal (like a Photoshop "cutout" / "remove background" tool) on this photo, keeping only this subject: '$targetSubject'. This is NOT a printed die-cut sticker — do not apply any printed-sticker styling such as a white border, colored outline, or halo around the edge. Treat this purely as isolating the subject from its background, nothing more.
        Preserve the subject's natural appeal from the original photo, including its pose, expression, color, texture, and recognizable silhouette.
        Keep the cutout clean and polished, but do not redraw, cartoonize, beautify unrealistically, or add new design elements.

        Background: unless the subject description above explicitly refers to a landscape, scenery, or wide view, treat everything else in the original photo as background and remove it completely — this includes walls, floors, furniture, other objects, other people, sky, ground, or any part of the scene not covered by the named subject itself. Only when the subject description itself is a landscape/scenery should a wider view remain, and even then only that described scene, not unrelated clutter around it.

        Orientation: keep the subject's overall up-down axis exactly as gravity would place it in real life. This is only about that axis, not the subject's pose — a sitting, lying, or reclining pose is fine. Do not rotate, tilt, flip, or invert the image so the subject reads as sideways or upside down — for example, a potted plant must stand upright, not sideways or upside down, and a standing person must have feet down and head up, not appear to stand on the ceiling.

        Edges: absolutely no outline, border, stroke, halo, or colored line of any kind may trace the subject's silhouette — not white, not any color. The cutout boundary itself must be the only edge, exactly like a background-removal tool would produce, not like a printed sticker. This applies especially to people: their body, hair, and clothing edges must transition directly from subject to background with zero added line art.

        Output format: the image must be a PNG cropped tightly to the subject's silhouette, with a genuine alpha-transparent background — every pixel outside the cutout must have alpha=0. Do not fill that area with white, gray, black, or any solid-color pixels, and do not bake in a gray/white checkerboard pattern as real pixels (that checkerboard is only an image-editor convention for showing transparency, never actual output content). Do not place the subject on any backdrop, canvas, frame, drop shadow, or decorative element, and do not leave a large rectangular area of transparent padding around it as if it still sits inside the original photo's frame — the result should read as a pure background-removed cutout, not a photo with a decorative border.
        """.trimIndent()

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
            - recap.badge: a short badge phrase, around 8 characters (in Korean)
            - recap.text: a single-sentence recap (in Korean)
            - sticker.targetSubject: a specific description of the subject to turn into a sticker (in Korean)
            - sticker.sourcePhotoId: the photo alias to use as the sticker source. It must be a value that actually appears in **this theme's own categorizedPhotoIds array**. Never use an alias that exists in the overall photo list but is NOT in this theme's categorizedPhotoIds (i.e., an alias belonging to a different theme) — always copy one of the aliases you just listed in categorizedPhotoIds above.
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
            - targetSubject: a specific description of the subject to turn into a sticker (in Korean)
            - sourcePhotoId: the photo alias to use as the sticker source. It must be a value that appears in the alias list above.
            - mainColor: the single most representative color of that subject as it actually appears in the source photo, as a 6-digit hex code (e.g. "#FF6B6B"). Pick the color a viewer would call "the color of this thing", not a shadow, highlight, or background color.
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
        - Subjects that are blurry, dark, or too small, so they'd look poor after cutout
        - Subjects where multiple objects overlap in a complex way with ambiguous boundaries
        - Scenes where the background itself is essential, so removing it would weaken the meaning
        - Lumping the entire captured scene (an entire table spread, an entire group of people, or any wide area that isn't actually a landscape) into a single "subject" — for example, describing it as "an entire table full of various foods" means the cutout would end up looking almost identical to the original photo
        Write targetSubject specifically enough that the cutout target is unambiguous, like "a person in red clothes smiling" or "a yellow character doll on a desk". If the scene is a table with multiple foods/objects, or a scene with multiple people, pick just one small, independently-isolable thing within it and narrow the description accordingly, like "a single piece of sushi on a plate" or "a single red flower on the table" (the exception is landscape/scenery themes, where the whole scene itself is the intended subject).
        """.trimIndent()

    private const val OUTPUT_LANGUAGE_NOTE = "Write all text output in Korean."
}
