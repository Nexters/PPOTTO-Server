package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.presentation.dto.RecapCommentResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapPhotoResponse
import com.github.nexters.ppotto.sticker.presentation.dto.StickerResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KFunction

private val STICKER_ID = UUID.fromString("01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")

private fun floatingComment(
    id: String,
    content: String,
    posX: Double,
    posY: Double,
) = RecapCommentResponse(UUID.fromString(id), content, posX, posY)

private fun keywordComment(
    id: String,
    content: String,
) = RecapCommentResponse(UUID.fromString(id), content, null, null)

private val FLOATING_COMMENTS =
    listOf(
        floatingComment("01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f", "야옹~", -96.0, -150.0),
        floatingComment("01983f2d-2b3c-7d4e-9f5a-6b7c8d9e0f1a", "또 주웠네!", -104.0, 62.0),
        floatingComment("01983f2d-3c4d-7e5f-a6b7-8c9d0e1f2a3b", "복슬복슬", 98.0, 18.0),
    )

private val KEYWORD_COMMENTS =
    listOf(
        keywordComment("01983f2d-4d5e-7f6a-b7c8-9d0e1f2a3b4c", "냥집사"),
        keywordComment("01983f2d-5e6f-7a7b-c8d9-0e1f2a3b4c5d", "웃긴 동물들"),
        keywordComment("01983f2d-6f7a-7b8c-d9e0-1f2a3b4c5d6e", "당신은 밈 수집가!"),
        keywordComment("01983f2d-7a8b-7c9d-e0f1-2a3b4c5d6e7f", "복슬복슬"),
        keywordComment("01983f2d-8b9c-7d0e-f1a2-3b4c5d6e7f8a", "저장한 동물 짤 중 62%가 고양이다냥!"),
        keywordComment("01983f2d-9c0d-7e1f-a2b3-4c5d6e7f8a9b", "감도 높은 취향"),
        keywordComment("01983f2d-a0d1-7f2a-b3c4-5d6e7f8a9b0c", "밈잘알"),
        keywordComment("01983f2d-b1e2-7a3b-c4d5-6e7f8a9b0c1d", "밈 고르는 안목 보소"),
        keywordComment("01983f2d-c2f3-7b4c-d5e6-7f8a9b0c1d2e", "근데 소랑 돌고래가 같이 나는 사진은 뭐임? 🤔"),
    )

private val RECAP_DETAIL_RESPONSE =
    ApiExample(
        name = "이미지 스티커 리캡",
        value =
            ApiResponse.success(
                RecapDetailResponse(
                    sticker =
                        StickerResponse(
                            id = StickerId(STICKER_ID),
                            title = "동물 밈 짤줍",
                            isNew = false,
                            type = StickerType.IMAGE,
                            imageUrl = "https://storage.googleapis.com/ppotto-stickers/01983f2b.png?X-Goog-Signature=sample",
                            textContent = null,
                            posX = 62.5,
                            posY = 318.0,
                            scale = 0.8,
                            rotation = -12.0,
                            zIndex = 3,
                            badgeOffsetX = -24.0,
                            badgeOffsetY = 96.0,
                            badgeRotation = 0.0,
                        ),
                    summary = "웃기고 귀여우면 일단 주워요",
                    comments = FLOATING_COMMENTS + KEYWORD_COMMENTS,
                    photos =
                        listOf(
                            RecapPhotoResponse(
                                id = PhotoId(UUID.fromString("01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f")),
                                imageUrl = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Signature=sample",
                                takenAt = Instant.parse("2026-06-14T04:22:10Z"),
                            ),
                            RecapPhotoResponse(
                                id = PhotoId(UUID.fromString("01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a")),
                                imageUrl = "https://storage.googleapis.com/ppotto-photos/01983f2f.jpg?X-Goog-Signature=sample",
                                takenAt = Instant.parse("2026-07-02T10:05:44Z"),
                            ),
                        ),
                ),
            ),
    )

private val UPDATE_STICKER_TITLE_REQUEST =
    ApiExample(
        name = "제목 수정",
        value = UpdateStickerTitleRequest(title = "고양이 모음집"),
    )

private val UPDATE_STICKER_TITLE_RESPONSE =
    ApiExample(
        name = "수정 완료",
        value = ApiResponse.success(UpdateStickerTitleResponse(id = StickerId(STICKER_ID), title = "고양이 모음집")),
    )

private val STICKER_NOT_FOUND_RESPONSE =
    listOf(
        ApiExamples.errorExample(
            code = "STICKER-001",
            summary = "스티커 없음 또는 소유자 불일치",
            message = "스티커를 찾을 수 없습니다.",
        ),
    )

private val STICKER_REGENERATION_IN_PROGRESS_RESPONSE =
    listOf(
        ApiExamples.errorExample(
            code = "STICKER-002",
            summary = "재생성 진행 중",
            message = "이미 재생성이 진행 중입니다.",
        ),
    )

@Component
class StickerApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            StickerApi::getRecap to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(RECAP_DETAIL_RESPONSE),
                            "404" to STICKER_NOT_FOUND_RESPONSE,
                        ),
                ),
            StickerApi::updateTitle to
                OperationExamples(
                    request = listOf(UPDATE_STICKER_TITLE_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(UPDATE_STICKER_TITLE_RESPONSE),
                            "400" to ApiExamples.INVALID_INPUT_RESPONSE,
                            "404" to STICKER_NOT_FOUND_RESPONSE,
                        ),
                ),
            StickerApi::regenerate to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(RECAP_DETAIL_RESPONSE),
                            "400" to ApiExamples.INVALID_INPUT_RESPONSE,
                            "404" to STICKER_NOT_FOUND_RESPONSE,
                            "409" to STICKER_REGENERATION_IN_PROGRESS_RESPONSE,
                        ),
                ),
            StickerApi::delete to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "404" to STICKER_NOT_FOUND_RESPONSE,
                        ),
                ),
            StickerApi::markViewed to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "404" to STICKER_NOT_FOUND_RESPONSE,
                        ),
                ),
        )
}
