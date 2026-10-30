package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.presentation.dto.BoardParticipantResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardParticipantsResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private val OWNER_ID = UserId(UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3c"))
private val MEMBER_ID = UserId(UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3d"))

private val BOARD_PARTICIPANTS_RESPONSE =
    ApiExample(
        name = "보드 참여자 목록",
        value =
            ApiResponse.success(
                BoardParticipantsResponse(
                    participantCount = 2,
                    maxParticipantCount = 8,
                    participants =
                        listOf(
                            BoardParticipantResponse(OWNER_ID, "김용희", BoardRole.OWNER),
                            BoardParticipantResponse(MEMBER_ID, "이수민", BoardRole.MEMBER),
                        ),
                ),
            ),
    )

private val PARTICIPANT_BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.NOT_FOUND,
        summary = "보드 없음 또는 접근 권한 없음",
    )

@Component
class BoardParticipantApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardParticipantApi::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(BOARD_PARTICIPANTS_RESPONSE),
                            "404" to listOf(PARTICIPANT_BOARD_NOT_FOUND),
                        ),
                ),
        )
}
