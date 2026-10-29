package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import java.util.UUID

object BoardInvitationPreviewObjectKeys {
    private const val NAMESPACE = "board-invitations"
    private val objectKeyGenerator = ObjectKeyGenerator()

    fun keyFor(invitationId: BoardInvitationId): String =
        objectKeyGenerator.generate(NAMESPACE, invitationId.toString(), id = UUID.randomUUID(), extension = "webp")
}
