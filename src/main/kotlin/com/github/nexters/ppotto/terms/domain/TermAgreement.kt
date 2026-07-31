package com.github.nexters.ppotto.terms.domain

import java.time.Instant
import java.util.UUID

data class TermAgreement(
    val id: UUID,
    val userId: UUID,
    val termId: UUID,
    val agreedAt: Instant,
)
