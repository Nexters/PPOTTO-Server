package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.model.BoardInvitationIssueResult
import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewUploadResult
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
