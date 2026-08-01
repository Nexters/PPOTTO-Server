package com.github.nexters.ppotto.terms.infrastructure.integration

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import org.springframework.stereotype.Component

@Component
class WithdrawnUserTermAgreementDeletionAdapter(
    private val termsService: TermsService,
) : WithdrawnUserTermAgreementDeletionPort {
    override fun deleteAllByUserId(userId: UserId): Unit = termsService.deleteAgreements(userId)
}
