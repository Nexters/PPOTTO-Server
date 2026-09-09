package com.github.nexters.ppotto.auth.domain

data class AuthSignup(
    val user: AuthUser,
    val pendingTerms: List<PendingTerm>,
)
