package com.github.nexters.ppotto.user.infrastructure.integration

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserStickerDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import org.springframework.stereotype.Component

@Component
class WithdrawnUserDataDeletionAdapter(
    private val boardDeletionPort: WithdrawnUserBoardDeletionPort,
    private val stickerDeletionPort: WithdrawnUserStickerDeletionPort,
    private val analysisDeletionPort: WithdrawnUserAnalysisDeletionPort,
    private val termAgreementDeletionPort: WithdrawnUserTermAgreementDeletionPort,
) : WithdrawnUserDataDeletionPort {
    override fun deleteAllFor(userId: UserId): Unit =
        boardDeletionPort
            .findAllBoardIds(userId)
            .also(stickerDeletionPort::deleteAllByBoardIds)
            .also { analysisDeletionPort.deleteAllByUserId(userId) }
            .also { boardDeletionPort.deleteAllByUserId(userId) }
            .let { termAgreementDeletionPort.deleteAllByUserId(userId) }
}
