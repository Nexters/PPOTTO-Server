package com.github.nexters.ppotto.sticker.presentation

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

private val RECAP_DETAIL_RESPONSE =
    ApiExample(
        name = "이미지 스티커 리캡",
        value =
            ApiResponse.success(
                RecapDetailResponse(
                    sticker =
                        StickerResponse(
                            id = STICKER_ID,
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
                    comments =
                        listOf(
                            RecapCommentResponse(
                                id = UUID.fromString("01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f"),
                                content = "웃기고 귀여우면 일단 주워요",
                                isFloat = true,
                                posX = 0.0,
                                posY = -140.0,
                            ),
                            RecapCommentResponse(
                                id = UUID.fromString("01983f2d-3c4d-7e5f-a6b7-8c9d0e1f2a3b"),
                                content = "또 고양이가 주워왔네요",
                                isFloat = false,
                                posX = null,
                                posY = null,
                            ),
                        ),
                    photos =
                        listOf(
                            RecapPhotoResponse(
                                id = UUID.fromString("01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f"),
                                imageUrl = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Signature=sample",
                                takenAt = Instant.parse("2026-06-14T04:22:10Z"),
                            ),
                            RecapPhotoResponse(
                                id = UUID.fromString("01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a"),
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
        value = ApiResponse.success(UpdateStickerTitleResponse(id = STICKER_ID, title = "고양이 모음집")),
    )

private val STICKER_NOT_FOUND_RESPONSE =
    listOf(
        ApiExamples.errorExample(
            code = "STICKER-001",
            summary = "스티커 없음 또는 소유자 불일치",
            message = "스티커를 찾을 수 없습니다.",
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
