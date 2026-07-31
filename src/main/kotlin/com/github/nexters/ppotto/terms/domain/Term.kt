package com.github.nexters.ppotto.terms.domain

import java.time.Instant
import java.util.UUID

data class Term(
    val id: UUID,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val effectiveAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
