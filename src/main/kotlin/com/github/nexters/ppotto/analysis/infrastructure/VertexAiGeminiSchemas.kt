package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.google.genai.types.Schema
import com.google.genai.types.Type

internal object VertexAiGeminiSchemas {
    val STICKER_RESPONSE_SCHEMA: Schema = stickerSchema(REGENERATION_SOURCE_PHOTO_ID_DESCRIPTION)

    val VERIFICATION_RESPONSE_SCHEMA: Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("Whether this one photo really contains the subject that was chosen earlier.")
            .properties(
                mapOf(
                    "subjectPresent" to
                        Schema
                            .builder()
                            .type(Type.Known.BOOLEAN)
                            .description(
                                "true if a usable sticker subject exists in this photo, " +
                                    "false if nothing here is isolable.",
                            ).build(),
                    "targetSubject" to stringSchema(TARGET_SUBJECT_DESCRIPTION),
                    "mainColor" to stringSchema(MAIN_COLOR_DESCRIPTION),
                ),
            ).propertyOrdering("subjectPresent", "targetSubject", "mainColor")
            .required("subjectPresent")
            .build()

    val CLASSIFICATION_RESPONSE_SCHEMA: Schema =
        Schema
            .builder()
            .type(Type.Known.ARRAY)
            .description("One entry per theme.")
            .items(themeSchema())
            .minItems(ThemeClassificationValidator.MIN_THEME_COUNT.toLong())
            .maxItems(ThemeClassificationValidator.MAX_THEME_COUNT.toLong())
            .build()

    private fun stringSchema(description: String): Schema =
        Schema
            .builder()
            .type(Type.Known.STRING)
            .description(description)
            .build()

    private fun numberSchema(description: String): Schema =
        Schema
            .builder()
            .type(Type.Known.NUMBER)
            .description(description)
            .build()

    private fun arraySchema(
        items: Schema,
        description: String,
        minItems: Long? = null,
        maxItems: Long? = null,
    ): Schema =
        Schema
            .builder()
            .type(Type.Known.ARRAY)
            .description(description)
            .items(items)
            .apply {
                minItems?.let { minItems(it) }
                maxItems?.let { maxItems(it) }
            }.build()

    private fun recapSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("The verdict card text shown to the user.")
            .properties(
                mapOf(
                    "badge" to stringSchema(BADGE_DESCRIPTION),
                    "text" to stringSchema(RECAP_TEXT_DESCRIPTION),
                ),
            ).propertyOrdering("badge", "text")
            .required("badge", "text")
            .build()

    private fun speechBubbleSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("One reaction floating next to the sticker.")
            .properties(
                mapOf(
                    "content" to stringSchema(SPEECH_BUBBLE_DESCRIPTION),
                    "posX" to numberSchema("Horizontal offset in pixels from the sticker center, between -150 and 150."),
                    "posY" to
                        numberSchema(
                            "Vertical offset in pixels from the sticker center, between -150 and 150, " +
                                "chosen so bubbles do not overlap each other.",
                        ),
                ),
            ).propertyOrdering("content", "posX", "posY")
            .required("content", "posX", "posY")
            .build()

    private fun commentsSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("Reactions and evidence shown around the sticker.")
            .properties(
                mapOf(
                    "speechBubbles" to
                        arraySchema(
                            speechBubbleSchema(),
                            "2 to 4 short Korean reactions floating around the sticker.",
                            minItems = 2,
                            maxItems = 4,
                        ),
                    "keywordChips" to
                        arraySchema(
                            stringSchema(KEYWORD_CHIP_DESCRIPTION),
                            "4 to 8 short Korean evidence phrases shown as chips below the sticker.",
                            minItems = 4,
                            maxItems = 8,
                        ),
                ),
            ).propertyOrdering("speechBubbles", "keywordChips")
            .required("speechBubbles", "keywordChips")
            .build()

    private fun stickerSchema(sourcePhotoIdDescription: String): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("Which photo to cut out and what to cut out of it.")
            .properties(
                mapOf(
                    "sourcePhotoId" to stringSchema(sourcePhotoIdDescription),
                    "targetSubject" to stringSchema(TARGET_SUBJECT_DESCRIPTION),
                    "mainColor" to stringSchema(MAIN_COLOR_DESCRIPTION),
                ),
            ).propertyOrdering("sourcePhotoId", "targetSubject", "mainColor")
            .required("sourcePhotoId", "targetSubject", "mainColor")
            .build()

    private fun themeSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .description("One theme: the photos in it, the verdict handed to the person, and the sticker to cut out.")
            .properties(
                mapOf(
                    "theme" to stringSchema(THEME_DESCRIPTION),
                    "categorizedPhotoIds" to
                        arraySchema(
                            stringSchema("One photo alias, copied exactly from the alias list in the prompt."),
                            "Photo aliases belonging to this theme, copied exactly from the alias list in the prompt.",
                        ),
                    "recap" to recapSchema(),
                    "sticker" to stickerSchema(THEME_SOURCE_PHOTO_ID_DESCRIPTION),
                    "comments" to commentsSchema(),
                ),
            ).propertyOrdering("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .required("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .build()
}

private const val REGENERATION_SOURCE_PHOTO_ID_DESCRIPTION =
    "One alias copied exactly from the alias list in the prompt."

private const val THEME_SOURCE_PHOTO_ID_DESCRIPTION =
    "One alias copied from this theme's categorizedPhotoIds above. " +
        "Never an alias that belongs to a different theme."

private const val TARGET_SUBJECT_DESCRIPTION =
    "Korean description of a subject literally visible in the sourcePhotoId photo. " +
        "This is an instruction for an automated cutout tool, not copy: keep it plain, literal, and humorless. " +
        "No jokes, no voice."

private const val MAIN_COLOR_DESCRIPTION =
    "6-digit hex code of the subject's dominant surface color as it appears in the photo, for example #FF6B6B. " +
        "Not a shadow, highlight, or background color."

private const val THEME_DESCRIPTION =
    "Theme name in Korean. Internal only, never shown to the user, " +
        "so name what literally happened rather than a mood."

private val BADGE_DESCRIPTION =
    "Sticker title in Korean: an award name or a verdict handed to the person who took these photos, " +
        "not a label for what the photos contain. " +
        "6 to 11 Korean characters plus exactly one trailing emoji, " +
        "never longer than ${RecapContent.MAX_BADGE_LENGTH} characters counting an emoji as 2. " +
        "A line only these photos could produce."

private const val RECAP_TEXT_DESCRIPTION =
    "One casual Korean sentence, 24 characters or fewer, 반말. " +
        "The evidence behind the verdict, aimed at the person who took the photos. " +
        "Names a real detail visible in them. " +
        "Never truncate mid-sentence: write it short instead."

private val SPEECH_BUBBLE_DESCRIPTION =
    "Short Korean reaction, 5 to 9 characters, never over ${ThemeComment.MAX_BUBBLE_LENGTH}. " +
        "Longer bubbles cover the sticker image on screen and get dropped by the server."

private val KEYWORD_CHIP_DESCRIPTION =
    "Short Korean evidence phrase, 2 to 6 characters, never over ${ThemeComment.MAX_CHIP_LENGTH}. " +
        "A concrete thing from the photos, not a category label. " +
        "Plain text only, never prefixed with # or any hashtag symbol."
