package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardInvitationService
import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewImage
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationPreviewUploadResponse
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class BoardInvitationPreviewController(
    private val boardInvitationService: BoardInvitationService,
) : BoardInvitationPreviewApi {
    override fun upload(
        @AuthenticatedUser userId: UserId,
        @PathVariable invitationId: BoardInvitationId,
        @RequestPart code: String,
        @RequestPart file: MultipartFile,
    ): ApiResponse<BoardInvitationPreviewUploadResponse> =
        boardInvitationService
            .uploadPreview(invitationId, userId, code, BoardInvitationPreviewImage.from(file.bytes))
            .let(BoardInvitationPreviewUploadResponse::from)
            .let { ApiResponse.success(it) }
}
