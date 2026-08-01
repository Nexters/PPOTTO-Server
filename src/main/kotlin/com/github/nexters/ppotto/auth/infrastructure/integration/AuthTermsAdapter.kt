package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.terms.application.TermResult
import com.github.nexters.ppotto.terms.application.TermsService
import org.springframework.stereotype.Component

@Component
class AuthTermsAdapter(
    private val termsService: TermsService,
) : AuthTermsPort {
    override fun findPendingTerms(userId: UserId): List<PendingTerm> = termsService.findPendingTerms(userId).map { it.toPendingTerm() }

    private fun TermResult.toPendingTerm() =
        PendingTerm(
            id = id,
            code = code,
            version = version,
            isRequired = isRequired,
            contentUrl = contentUrl,
            agreed = agreed,
        )
}
