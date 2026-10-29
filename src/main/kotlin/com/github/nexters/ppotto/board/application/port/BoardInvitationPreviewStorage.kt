package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.global.identifier.BoardInvitationId

interface BoardInvitationPreviewStorage {
    fun upload(
        invitationId: BoardInvitationId,
        bytes: ByteArray,
    ): String

    fun issueReadUrl(objectKey: String): String

    fun delete(objectKey: String)
}
