package com.github.nexters.ppotto.auth.domain

import com.github.nexters.ppotto.global.identifier.TermId

data class PendingTerm(
    val id: TermId,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val agreed: Boolean,
)
