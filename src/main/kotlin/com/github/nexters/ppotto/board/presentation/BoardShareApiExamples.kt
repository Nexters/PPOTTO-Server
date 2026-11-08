package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.presentation.dto.BoardShareRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardShareResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardStickerResponse
import com.github.nexters.ppotto.board.presentation.dto.DrawingV2Response
import com.github.nexters.ppotto.board.presentation.dto.SharedBoardResponse
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private const val SHARE_TOKEN = "4f1c2b9e-7a3d-4e8f-9b6a-2c5d8e1f0a7b"

private val SHARE_BOARD_REQUEST =
    ApiExample(name = "리캡까지 공유", value = BoardShareRequest(scope = BoardShareScope.RECAP))

private val SHARE_BOARD_RESPONSE =
    ApiExample(
        name = "공유 링크 발급",
        value = ApiResponse.success(BoardShareResponse(shareToken = SHARE_TOKEN, scope = BoardShareScope.RECAP)),
    )

private val SHARED_BOARD_RESPONSE =
    ApiExample(
        name = "리캡까지 공유된 보드",
        value =
            ApiResponse.success(
                SharedBoardResponse(
                    boardName = "제주 여행",
                    sharerName = "김용희",
                    scope = BoardShareScope.RECAP,
                    stickers =
                        listOf(
                            BoardStickerResponse(
                                id = StickerId(UUID.fromString("01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")),
                                title = "동물 밈 짤줍",
                                isNew = false,
                                type = BoardStickerType.IMAGE,
                                imageUrl =
                                    "https://storage.googleapis.com/ppotto-stickers/01983f2b.png" +
                                        "?X-Goog-Expires=3600&X-Goog-Signature=sample",
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
                        ),
                    drawings =
                        listOf(
                            DrawingV2Response.Text(
                                id = DrawingId(UUID.fromString("01983f2c-5e6f-7a8b-9c0d-1e2f3a4b5c6d")),
                                scope = DrawingScope.BOARD,
                                stickerId = null,
                                color = "#FFFFFF",
                                zIndex = 7,
                                content = "여름 휴가",
                                fontSize = 26.0,
                                posX = 80.0,
                                posY = 290.5,
                                maxWidth = 280.0,
                                rotation = 0.0,
                            ),
                        ),
                ),
            ),
    )

private val SHARE_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.SHARE_NOT_FOUND,
        summary = "없는 토큰, 보드 삭제, 공유자 탈퇴 또는 보드 나가기",
    )

private val BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.NOT_FOUND,
        summary = "보드 없음 또는 공유 권한 없음",
    )

@Component
class BoardShareApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardShareApi::share to
                OperationExamples(
                    request = listOf(SHARE_BOARD_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(SHARE_BOARD_RESPONSE),
                            "400" to ApiExamples.INVALID_INPUT_RESPONSE,
                            "404" to listOf(BOARD_NOT_FOUND),
                        ),
                ),
            BoardShareQueryApi::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(SHARED_BOARD_RESPONSE),
                            "404" to listOf(SHARE_NOT_FOUND),
                        ),
                ),
        )
}
