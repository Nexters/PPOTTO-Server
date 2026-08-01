package com.github.nexters.ppotto.terms.domain

import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.identifier.UserId
import java.time.Instant
import java.util.UUID

data class TermAgreement(
    val id: UUID,
    val userId: UserId,
    val termId: TermId,
    val agreedAt: Instant,
)
