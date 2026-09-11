package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.google.genai.types.Schema
import com.google.genai.types.Type

internal object VertexAiGeminiSchemas {
    val STICKER_RESPONSE_SCHEMA: Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "sourcePhotoId" to stringSchema(),
                    "targetSubject" to stringSchema(),
                    "mainColor" to stringSchema(),
                ),
            ).propertyOrdering("sourcePhotoId", "targetSubject", "mainColor")
            .required("sourcePhotoId", "targetSubject", "mainColor")
            .build()

    val VERIFICATION_RESPONSE_SCHEMA: Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "subjectPresent" to
                        Schema
                            .builder()
                            .type(Type.Known.BOOLEAN)
                            .build(),
                    "targetSubject" to stringSchema(),
                    "mainColor" to stringSchema(),
                ),
            ).propertyOrdering("subjectPresent", "targetSubject", "mainColor")
            .required("subjectPresent")
            .build()

    val CLASSIFICATION_RESPONSE_SCHEMA: Schema =
        Schema
            .builder()
            .type(Type.Known.ARRAY)
            .items(themeSchema())
            .minItems(ThemeClassificationValidator.MIN_THEME_COUNT.toLong())
            .maxItems(ThemeClassificationValidator.MAX_THEME_COUNT.toLong())
            .build()

    private fun stringSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.STRING)
            .build()

    private fun arraySchema(items: Schema): Schema =
        Schema
            .builder()
            .type(Type.Known.ARRAY)
            .items(items)
            .build()

    private fun recapSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "badge" to stringSchema(),
                    "text" to stringSchema(),
                ),
            ).propertyOrdering("badge", "text")
            .required("badge", "text")
            .build()

    private fun speechBubbleSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "content" to stringSchema(),
                    "posX" to
                        Schema
                            .builder()
                            .type(Type.Known.NUMBER)
                            .build(),
                    "posY" to
                        Schema
                            .builder()
                            .type(Type.Known.NUMBER)
                            .build(),
                ),
            ).propertyOrdering("content", "posX", "posY")
            .required("content", "posX", "posY")
            .build()

    private fun commentsSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "speechBubbles" to arraySchema(speechBubbleSchema()),
                    "keywordChips" to arraySchema(stringSchema()),
                ),
            ).propertyOrdering("speechBubbles", "keywordChips")
            .required("speechBubbles", "keywordChips")
            .build()

    private fun themeSchema(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "theme" to stringSchema(),
                    "categorizedPhotoIds" to arraySchema(stringSchema()),
                    "recap" to recapSchema(),
                    "sticker" to STICKER_RESPONSE_SCHEMA,
                    "comments" to commentsSchema(),
                ),
            ).propertyOrdering("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .required("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .build()
}
