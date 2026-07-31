package com.github.nexters.ppotto.terms.infrastructure.integration

import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WithdrawnUserTermAgreementDeletionAdapter(
    private val termsService: TermsService,
) : WithdrawnUserTermAgreementDeletionPort {
    override fun deleteAllByUserId(userId: UUID): Unit = termsService.deleteAgreements(userId)
}
