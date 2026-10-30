package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.presentation.dto.BoardJoinResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private val JOINED_BOARD_ID = BoardId(UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b"))

private val BOARD_JOIN_RESPONSE =
    ApiExample(
        name = "보드 참여",
        value =
            ApiResponse.success(
                BoardJoinResponse(
                    boardId = JOINED_BOARD_ID,
                    boardName = "제주 여행",
                    role = BoardRole.MEMBER,
                ),
            ),
    )

private val JOIN_INVITATION_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.INVITATION_NOT_FOUND,
        summary = "초대 없음, 만료, 보드 삭제 또는 발급자 탈퇴",
    )

private val PARTICIPANT_LIMIT_EXCEEDED =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.PARTICIPANT_LIMIT_EXCEEDED,
        summary = "보드 참여 인원 8명 도달",
    )

@Component
class BoardParticipationApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardParticipationApi::join to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(BOARD_JOIN_RESPONSE),
                            "404" to listOf(JOIN_INVITATION_NOT_FOUND),
                            "409" to listOf(PARTICIPANT_LIMIT_EXCEEDED),
                        ),
                ),
        )
}
