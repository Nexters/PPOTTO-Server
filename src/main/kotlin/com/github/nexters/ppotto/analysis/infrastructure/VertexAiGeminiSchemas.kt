package com.github.nexters.ppotto.analysis.infrastructure

import com.google.genai.types.Schema
import com.google.genai.types.Type

internal object VertexAiGeminiSchemas {
    private val RECAP_SCHEMA =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "badge" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                    "text" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                ),
            ).propertyOrdering("badge", "text")
            .required("badge", "text")
            .build()

    fun stickerResponseSchema() =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "sourcePhotoId" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                    "targetSubject" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                    "mainColor" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                ),
            ).propertyOrdering("sourcePhotoId", "targetSubject", "mainColor")
            .required("sourcePhotoId", "targetSubject", "mainColor")
            .build()

    fun verificationResponseSchema() =
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
                    "targetSubject" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                    "mainColor" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                ),
            ).propertyOrdering("subjectPresent", "targetSubject", "mainColor")
            .required("subjectPresent")
            .build()

    private val SPEECH_BUBBLE_SCHEMA =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "content" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
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

    private val COMMENTS_SCHEMA =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "speechBubbles" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(SPEECH_BUBBLE_SCHEMA)
                            .build(),
                    "keywordChips" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING))
                            .build(),
                ),
            ).propertyOrdering("speechBubbles", "keywordChips")
            .required("speechBubbles", "keywordChips")
            .build()

    private fun themeSchema() =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "theme" to
                        Schema
                            .builder()
                            .type(Type.Known.STRING)
                            .build(),
                    "categorizedPhotoIds" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(
                                Schema
                                    .builder()
                                    .type(Type.Known.STRING),
                            ).build(),
                    "recap" to RECAP_SCHEMA,
                    "sticker" to stickerResponseSchema(),
                    "comments" to COMMENTS_SCHEMA,
                ),
            ).propertyOrdering("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .required("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
            .build()

    fun classificationResponseSchema() =
        Schema
            .builder()
            .type(Type.Known.ARRAY)
            .items(themeSchema())
            .build()
}
