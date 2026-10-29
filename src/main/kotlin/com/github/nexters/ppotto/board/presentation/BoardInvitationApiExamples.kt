package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.model.BoardInvitationViewerStatus
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationIssueResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationParticipantResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationPreviewUploadResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KFunction

private val INVITATION_ID = BoardInvitationId(UUID.fromString("019a268d-15c8-7d31-8f42-90ad72d627f5"))
private val INVITED_BOARD_ID = BoardId(UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b"))
private val INVITATION_EXPIRES_AT = Instant.parse("2026-11-27T09:30:00Z")
private const val INVITATION_PREVIEW_URL =
    "https://storage.googleapis.com/ppotto-board-invitations/preview.webp?X-Goog-Signature=sample"

private val INVITATION_ISSUE_RESPONSE =
    ApiExample(
        name = "초대 코드 발급",
        value =
            ApiResponse.success(
                BoardInvitationIssueResponse(
                    invitationId = INVITATION_ID,
                    code = "K7P2QX",
                    expiresAt = INVITATION_EXPIRES_AT,
                    previewRequired = true,
                ),
            ),
    )

private val INVITATION_PREVIEW_UPLOAD_RESPONSE =
    ApiExample(
        name = "초대 미리보기 업로드",
        value = ApiResponse.success(BoardInvitationPreviewUploadResponse(INVITATION_PREVIEW_URL)),
    )

private val INVITATION_DETAIL_AUTHENTICATED_RESPONSE =
    ApiExample(
        name = "로그인 사용자 - 참여 상태와 참여자 포함",
        value =
            ApiResponse.success(
                invitationDetail(
                    viewerStatus = BoardInvitationViewerStatus.NOT_JOINED,
                    participants =
                        listOf(
                            BoardInvitationParticipantResponse(name = "김용희", role = BoardRole.OWNER),
                            BoardInvitationParticipantResponse(name = "이수민", role = BoardRole.MEMBER),
                        ),
                ),
            ),
    )

private val INVITATION_DETAIL_ANONYMOUS_RESPONSE =
    ApiExample(
        name = "비로그인 사용자 - 공개 정보만",
        value = ApiResponse.success(invitationDetail(viewerStatus = null, participants = null)),
    )

private fun invitationDetail(
    viewerStatus: BoardInvitationViewerStatus?,
    participants: List<BoardInvitationParticipantResponse>?,
): BoardInvitationDetailResponse =
    BoardInvitationDetailResponse(
        code = "K7P2QX",
        boardId = INVITED_BOARD_ID,
        boardName = "제주 여행",
        inviterName = "김용희",
        participantCount = 2,
        maxParticipantCount = 8,
        stickerCount = 9,
        previewImageUrl = INVITATION_PREVIEW_URL,
        expiresAt = INVITATION_EXPIRES_AT,
        viewerStatus = viewerStatus,
        participants = participants,
    )

private val BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.NOT_FOUND,
        summary = "보드 없음 또는 초대 권한 없음",
    )

private val INVITATION_NOT_FOUND =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.INVITATION_NOT_FOUND,
        summary = "초대 없음, 만료, 보드 삭제 또는 발급자 탈퇴",
    )

private val INVALID_INVITATION_PREVIEW =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.INVALID_INVITATION_PREVIEW,
        summary = "WebP 형식, 파일 크기 또는 해상도 검증 실패",
    )

private val INVITATION_CODE_RENEWED =
    ApiExamples.errorExample(
        errorCode = BoardErrorCode.INVITATION_CODE_RENEWED,
        summary = "미리보기 생성 중 초대 코드가 갱신됨",
    )

@Component
class BoardInvitationApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardInvitationApi::issue to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(INVITATION_ISSUE_RESPONSE),
                            "404" to listOf(BOARD_NOT_FOUND),
                        ),
                ),
            BoardInvitationPreviewApi::upload to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(INVITATION_PREVIEW_UPLOAD_RESPONSE),
                            "400" to listOf(INVALID_INVITATION_PREVIEW),
                            "404" to listOf(INVITATION_NOT_FOUND),
                            "409" to listOf(INVITATION_CODE_RENEWED),
                        ),
                ),
            BoardInvitationQueryApi::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to
                                listOf(
                                    INVITATION_DETAIL_AUTHENTICATED_RESPONSE,
                                    INVITATION_DETAIL_ANONYMOUS_RESPONSE,
                                ),
                            "404" to listOf(INVITATION_NOT_FOUND),
                        ),
                ),
        )
}
