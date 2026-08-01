package com.github.nexters.ppotto.terms.domain

import com.github.nexters.ppotto.global.identifier.TermId
import java.time.Instant

data class Term(
    val id: TermId,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val effectiveAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
