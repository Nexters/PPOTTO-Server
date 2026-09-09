package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.global.identifier.UserId

fun interface AuthTermsPort {
    fun findPendingTerms(userId: UserId): List<PendingTerm>
}
