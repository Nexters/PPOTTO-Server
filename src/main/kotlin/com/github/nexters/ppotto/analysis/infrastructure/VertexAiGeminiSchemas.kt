package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.google.genai.types.Schema

internal object VertexAiGeminiSchemas {
    const val MIN_SPEECH_BUBBLE_COUNT = 2L
    const val MAX_SPEECH_BUBBLE_COUNT = 4L
    const val MIN_KEYWORD_CHIP_COUNT = 4L
    const val MAX_KEYWORD_CHIP_COUNT = 8L
    const val MIN_OBSERVED_DETAIL_COUNT = 3L
    const val MAX_OBSERVED_DETAIL_COUNT = 6L

    val STICKER_RESPONSE_SCHEMA: Schema = stickerSchema(REGENERATION_SOURCE_PHOTO_ID)

    val VERIFICATION_RESPONSE_SCHEMA: Schema =
        objectSchema<GeminiSubjectVerificationResponse>(
            "Whether this one photo really contains the subject that was chosen earlier.",
        ) {
            boolean(
                GeminiSubjectVerificationResponse::subjectPresent,
                "true if a usable sticker subject exists in this photo, false if nothing here is isolable.",
            )
            string(GeminiSubjectVerificationResponse::targetSubject, TARGET_SUBJECT, required = false)
            string(GeminiSubjectVerificationResponse::mainColor, MAIN_COLOR, required = false)
        }

    val CLASSIFICATION_RESPONSE_SCHEMA: Schema =
        arraySchema(
            description = "One entry per theme, in the order you want them shown.",
            items = themeSchema(),
            minItems = ThemeClassificationValidator.MIN_THEME_COUNT.toLong(),
            maxItems = ThemeClassificationValidator.MAX_THEME_COUNT.toLong(),
        )

    private fun themeSchema(): Schema =
        objectSchema<GeminiThemeResponse>(
            "One theme: the photos in it, the verdict handed to the person who took them, and the sticker to cut out.",
        ) {
            strings(
                GeminiThemeResponse::observedDetails,
                description =
                    "$MIN_OBSERVED_DETAIL_COUNT to $MAX_OBSERVED_DETAIL_COUNT concrete things you can actually see " +
                        "across this theme's photos, in Korean: objects, foods, places, weather, time of day, " +
                        "what people are doing. Plain nouns only, no adjectives, no interpretation. " +
                        "This is your scratchpad: every field below must be built out of these, " +
                        "not out of generic mood words.",
                itemDescription = "One concrete thing you can actually see, as a plain Korean noun phrase.",
                minItems = MIN_OBSERVED_DETAIL_COUNT,
                maxItems = MAX_OBSERVED_DETAIL_COUNT,
            )
            string(
                GeminiThemeResponse::theme,
                "Theme name in Korean. Internal only, never shown to the user, " +
                    "so name what literally happened rather than a mood.",
            )
            strings(
                GeminiThemeResponse::categorizedPhotoIds,
                description = "Photo aliases belonging to this theme, copied exactly from the alias list in the prompt.",
                itemDescription = PHOTO_ALIAS,
            )
            nested(GeminiThemeResponse::recap, recapSchema())
            nested(GeminiThemeResponse::sticker, stickerSchema(THEME_SOURCE_PHOTO_ID))
            nested(GeminiThemeResponse::comments, commentsSchema())
        }

    private fun recapSchema(): Schema =
        objectSchema<GeminiRecapResponse>("The verdict card text shown to the user.") {
            string(
                GeminiRecapResponse::badge,
                "Sticker title in Korean: an award name or a verdict handed to the person who took these photos, " +
                    "not a label for what the photos contain. " +
                    "6 to 11 Korean characters plus exactly one trailing emoji, " +
                    "never longer than ${RecapContent.MAX_BADGE_LENGTH} characters counting an emoji as 2. " +
                    "A line only these photos could produce.",
            )
            string(
                GeminiRecapResponse::text,
                "One casual Korean sentence, 24 characters or fewer, 반말. " +
                    "The evidence behind the verdict, aimed at the person who took the photos. " +
                    "Names a real detail visible in them. " +
                    "Never truncate mid-sentence: write it short instead.",
            )
        }

    private fun stickerSchema(sourcePhotoIdDescription: String): Schema =
        objectSchema<GeminiStickerResponse>("Which photo to cut out and what to cut out of it.") {
            string(GeminiStickerResponse::sourcePhotoId, sourcePhotoIdDescription)
            string(GeminiStickerResponse::targetSubject, TARGET_SUBJECT)
            string(GeminiStickerResponse::mainColor, MAIN_COLOR)
        }

    private fun commentsSchema(): Schema =
        objectSchema<GeminiCommentsResponse>("Reactions and evidence shown around the sticker.") {
            objects(
                GeminiCommentsResponse::speechBubbles,
                description =
                    "$MIN_SPEECH_BUBBLE_COUNT to $MAX_SPEECH_BUBBLE_COUNT short Korean reactions " +
                        "floating around the sticker.",
                items = speechBubbleSchema(),
                minItems = MIN_SPEECH_BUBBLE_COUNT,
                maxItems = MAX_SPEECH_BUBBLE_COUNT,
            )
            strings(
                GeminiCommentsResponse::keywordChips,
                description =
                    "$MIN_KEYWORD_CHIP_COUNT to $MAX_KEYWORD_CHIP_COUNT short Korean evidence phrases " +
                        "shown as chips below the sticker.",
                itemDescription =
                    "Short Korean evidence phrase, 2 to 6 characters, never over ${ThemeComment.MAX_CHIP_LENGTH}. " +
                        "A concrete thing from the photos, not a category label. " +
                        "Plain text only, never prefixed with # or any hashtag symbol.",
                minItems = MIN_KEYWORD_CHIP_COUNT,
                maxItems = MAX_KEYWORD_CHIP_COUNT,
            )
        }

    private fun speechBubbleSchema(): Schema =
        objectSchema<GeminiSpeechBubbleResponse>("One reaction floating next to the sticker.") {
            string(
                GeminiSpeechBubbleResponse::content,
                "Short Korean reaction, 5 to 9 characters, never over ${ThemeComment.MAX_BUBBLE_LENGTH}. " +
                    "Longer bubbles cover the sticker image on screen and get dropped by the server.",
            )
            number(
                GeminiSpeechBubbleResponse::posX,
                "Horizontal offset in pixels from the sticker center, between -150 and 150.",
            )
            number(
                GeminiSpeechBubbleResponse::posY,
                "Vertical offset in pixels from the sticker center, between -150 and 150, " +
                    "chosen so bubbles do not overlap each other.",
            )
        }
}

private const val PHOTO_ALIAS = "One photo alias, copied exactly from the alias list in the prompt."

private const val REGENERATION_SOURCE_PHOTO_ID = "One alias copied exactly from the alias list in the prompt."

private const val THEME_SOURCE_PHOTO_ID =
    "One alias copied from this theme's categorizedPhotoIds above. " +
        "Never an alias that belongs to a different theme."

private const val TARGET_SUBJECT =
    "Korean description of a subject literally visible in the sourcePhotoId photo. " +
        "This is an instruction for an automated cutout tool, not copy: keep it plain, literal, and humorless. " +
        "No jokes, no voice."

private const val MAIN_COLOR =
    "6-digit hex code of the subject's dominant surface color as it appears in the photo, for example #FF6B6B. " +
        "Not a shadow, highlight, or background color."
