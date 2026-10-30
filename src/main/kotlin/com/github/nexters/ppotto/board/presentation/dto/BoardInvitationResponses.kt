package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.model.BoardInvitationDetail
import com.github.nexters.ppotto.board.application.model.BoardInvitationIssueResult
import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewUploadResult
import com.github.nexters.ppotto.board.application.model.BoardInvitationViewerStatus
import com.github.nexters.ppotto.board.application.model.BoardParticipant
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "보드 초대 코드 발급 결과")
data class BoardInvitationIssueResponse(
    @get:Schema(description = "초대 ID (uuidv7)", example = "019a268d-15c8-7d31-8f42-90ad72d627f5")
    @get:JsonProperty("invitationId")
    val invitationId: BoardInvitationId,

    @field:Schema(description = "초대 코드", example = "K7P2QX")
    val code: String,

    @field:Schema(description = "초대 만료 시각", example = "2026-11-27T09:30:00Z")
    val expiresAt: Instant,

    @field:Schema(description = "현재 코드의 미리보기 생성 필요 여부", example = "true")
    val previewRequired: Boolean,
) {
    companion object {
        fun from(result: BoardInvitationIssueResult): BoardInvitationIssueResponse =
            BoardInvitationIssueResponse(
                invitationId = result.invitationId,
                code = result.code,
                expiresAt = result.expiresAt,
                previewRequired = result.previewRequired,
            )
    }
}

@Schema(description = "보드 초대 미리보기 업로드 결과")
data class BoardInvitationPreviewUploadResponse(
    @field:Schema(
        description = "미리보기 읽기용 signed URL",
        example = "https://storage.googleapis.com/ppotto-board-invitations/...?X-Goog-Signature=sample",
    )
    val previewImageUrl: String,
) {
    companion object {
        fun from(result: BoardInvitationPreviewUploadResult): BoardInvitationPreviewUploadResponse =
            BoardInvitationPreviewUploadResponse(result.previewImageUrl)
    }
}

@Schema(description = "보드 초대 상세")
data class BoardInvitationDetailResponse(
    @field:Schema(description = "초대 코드", example = "K7P2QX")
    val code: String,

    @get:Schema(description = "초대 대상 보드 ID (uuidv7)")
    @get:JsonProperty("boardId")
    val boardId: BoardId,

    @field:Schema(description = "보드 이름", example = "제주 여행")
    val boardName: String,

    @field:Schema(description = "초대한 사용자 이름", example = "김용희")
    val inviterName: String,

    @field:Schema(description = "현재 참여 인원(방장 포함)", example = "2")
    val participantCount: Int,

    @field:Schema(description = "최대 참여 인원(방장 포함)", example = "8")
    val maxParticipantCount: Int,

    @field:Schema(description = "현재 스티커 수", example = "9")
    val stickerCount: Int,

    @field:Schema(description = "미리보기 읽기용 signed URL", nullable = true)
    val previewImageUrl: String?,

    @field:Schema(description = "초대 만료 시각", example = "2026-11-27T09:30:00Z")
    val expiresAt: Instant,

    @field:Schema(
        description = "로그인한 요청 사용자의 보드 참여 상태. 익명 요청에서는 생략",
        enumAsRef = true,
        nullable = true,
    )
    val viewerStatus: BoardInvitationViewerStatus?,

    @field:Schema(description = "현재 참여자 목록. 익명 요청에서는 생략", nullable = true)
    val participants: List<BoardInvitationParticipantResponse>?,
) {
    companion object {
        fun from(detail: BoardInvitationDetail): BoardInvitationDetailResponse =
            BoardInvitationDetailResponse(
                code = detail.code,
                boardId = detail.boardId,
                boardName = detail.boardName,
                inviterName = detail.inviterName,
                participantCount = detail.participantCount,
                maxParticipantCount = detail.maxParticipantCount,
                stickerCount = detail.stickerCount,
                previewImageUrl = detail.previewImageUrl,
                expiresAt = detail.expiresAt,
                viewerStatus = detail.viewerStatus,
                participants = detail.participants?.map(BoardInvitationParticipantResponse::from),
            )
    }
}

@Schema(description = "보드 초대 참여자")
data class BoardInvitationParticipantResponse(
    @field:Schema(description = "사용자 이름", example = "김용희")
    val name: String,

    @field:Schema(description = "보드 역할", enumAsRef = true)
    val role: BoardRole,
) {
    companion object {
        fun from(participant: BoardParticipant): BoardInvitationParticipantResponse =
            BoardInvitationParticipantResponse(
                name = participant.name,
                role = participant.role,
            )
    }
}
