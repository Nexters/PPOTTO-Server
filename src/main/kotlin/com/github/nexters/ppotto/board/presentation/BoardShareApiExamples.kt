package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.presentation.dto.BoardShareRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardShareResponse
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.stereotype.Component
import kotlin.reflect.KFunction

private const val SHARE_TOKEN = "4f1c2b9e-7a3d-4e8f-9b6a-2c5d8e1f0a7b"

private val SHARE_BOARD_REQUEST =
    ApiExample(name = "리캡까지 공유", value = BoardShareRequest(scope = BoardShareScope.RECAP))

private val SHARE_BOARD_RESPONSE =
    ApiExample(
        name = "공유 링크 발급",
        value = ApiResponse.success(BoardShareResponse(shareToken = SHARE_TOKEN, scope = BoardShareScope.RECAP)),
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
        )
}
