package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeComment

internal const val MIN_OBSERVED_DETAIL_COUNT = 3
internal const val MAX_OBSERVED_DETAIL_COUNT = 6
internal const val MIN_SPEECH_BUBBLE_COUNT = 2
internal const val MAX_SPEECH_BUBBLE_COUNT = 4
internal const val MIN_KEYWORD_CHIP_COUNT = 4
internal const val MAX_KEYWORD_CHIP_COUNT = 8

private const val PHOTO_ALIAS = "One photo alias, copied exactly from the alias list in the prompt."

private const val TARGET_SUBJECT =
    "Korean description of a subject literally visible in the sourcePhotoId photo. " +
        "This is an instruction for an automated cutout tool, not copy: keep it plain, literal, and humorless. " +
        "No jokes, no voice."

private const val MAIN_COLOR =
    "6-digit hex code of the subject's dominant surface color as it appears in the photo, for example #FF6B6B. " +
        "Not a shadow, highlight, or background color."

@GeminiObject("One theme: the photos in it, the verdict handed to the person who took them, and the sticker to cut out.")
internal data class GeminiThemeResponse(
    @GeminiField(
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
    val observedDetails: List<String>? = null,

    @GeminiField(
        description =
            "Theme name in Korean. Internal only, never shown to the user, " +
                "so name what literally happened rather than a mood.",
    )
    val theme: String,

    @GeminiField(
        description = "Photo aliases belonging to this theme, copied exactly from the alias list in the prompt.",
        itemDescription = PHOTO_ALIAS,
    )
    val categorizedPhotoIds: List<String>,

    @GeminiField(description = "The verdict card text shown to the user.")
    val recap: GeminiRecapResponse,

    @GeminiField(description = "Which photo to cut out and what to cut out of it.")
    val sticker: GeminiStickerResponse,

    @GeminiField(description = "Reactions and evidence shown around the sticker.")
    val comments: GeminiCommentsResponse?,
)

@GeminiObject("The verdict card text shown to the user.")
internal data class GeminiRecapResponse(
    @GeminiField(
        description =
            "Sticker title in Korean: an award name or a verdict handed to the person who took these photos, " +
                "not a label for what the photos contain. " +
                "6 to 11 Korean characters plus exactly one trailing emoji, " +
                "never longer than ${RecapContent.MAX_BADGE_LENGTH} characters counting an emoji as 2. " +
                "A line only these photos could produce.",
    )
    val badge: String,

    @GeminiField(
        description =
            "One casual Korean sentence, 24 characters or fewer, 반말. " +
                "The evidence behind the verdict, aimed at the person who took the photos. " +
                "Names a real detail visible in them. " +
                "Never truncate mid-sentence: write it short instead.",
    )
    val text: String,
)

@GeminiObject("Which photo to cut out and what to cut out of it.")
internal data class GeminiStickerResponse(
    @GeminiField(
        description =
            "One alias copied from this theme's categorizedPhotoIds above. " +
                "Never an alias that belongs to a different theme.",
    )
    val sourcePhotoId: String,

    @GeminiField(description = TARGET_SUBJECT)
    val targetSubject: String,

    @GeminiField(description = MAIN_COLOR)
    val mainColor: String?,
)

@GeminiObject("Which photo to cut out and what to cut out of it.")
internal data class GeminiStickerRegenerationResponse(
    @GeminiField(description = "One alias copied exactly from the alias list in the prompt.")
    val sourcePhotoId: String,

    @GeminiField(description = TARGET_SUBJECT)
    val targetSubject: String,

    @GeminiField(description = MAIN_COLOR)
    val mainColor: String?,
)

@GeminiObject("Whether this one photo really contains the subject that was chosen earlier.")
internal data class GeminiSubjectVerificationResponse(
    @GeminiField(
        description = "true if a usable sticker subject exists in this photo, false if nothing here is isolable.",
    )
    val subjectPresent: Boolean?,

    @GeminiField(description = TARGET_SUBJECT, required = false)
    val targetSubject: String?,

    @GeminiField(description = MAIN_COLOR, required = false)
    val mainColor: String?,
)

@GeminiObject("Reactions and evidence shown around the sticker.")
internal data class GeminiCommentsResponse(
    @GeminiField(
        description =
            "$MIN_SPEECH_BUBBLE_COUNT to $MAX_SPEECH_BUBBLE_COUNT short Korean reactions " +
                "floating around the sticker.",
        minItems = MIN_SPEECH_BUBBLE_COUNT,
        maxItems = MAX_SPEECH_BUBBLE_COUNT,
    )
    val speechBubbles: List<GeminiSpeechBubbleResponse>?,

    @GeminiField(
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
    val keywordChips: List<String>?,
)

@GeminiObject("One reaction floating next to the sticker.")
internal data class GeminiSpeechBubbleResponse(
    @GeminiField(
        description =
            "Short Korean reaction, 5 to 9 characters, never over ${ThemeComment.MAX_BUBBLE_LENGTH}. " +
                "Longer bubbles cover the sticker image on screen and get dropped by the server.",
    )
    val content: String?,

    @GeminiField(description = "Horizontal offset in pixels from the sticker center, between -150 and 150.")
    val posX: Double?,

    @GeminiField(
        description =
            "Vertical offset in pixels from the sticker center, between -150 and 150, " +
                "chosen so bubbles do not overlap each other.",
    )
    val posY: Double?,
)
