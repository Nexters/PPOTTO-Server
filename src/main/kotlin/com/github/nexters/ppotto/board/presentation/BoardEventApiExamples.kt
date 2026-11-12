package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import org.springframework.stereotype.Component
import kotlin.reflect.KFunction

private val BOARD_EVENT_STREAM =
    ApiExample(
        name = "레이아웃 확정",
        value =
            listOf(
                "id: 019a72c0-4d8e-7f10-a2b3-c4d5e6f70819",
                "event: BOARD_LAYOUT_UPDATED",
                "retry: 3000",
                "data: " +
                    "{\"eventId\":\"019a72c0-4d8e-7f10-a2b3-c4d5e6f70819\"," +
                    "\"type\":\"BOARD_LAYOUT_UPDATED\"," +
                    "\"boardId\":\"01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b\"," +
                    "\"occurredAt\":\"2026-11-12T12:00:00Z\"}",
                "",
            ).joinToString("\n"),
    )

private val BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.NOT_FOUND,
        summary = "보드 없음 또는 접근 권한 없음",
    )

@Component
class BoardEventApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardEventApi::subscribe to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(BOARD_EVENT_STREAM),
                            "404" to listOf(BOARD_NOT_FOUND),
                        ),
                ),
        )
}
