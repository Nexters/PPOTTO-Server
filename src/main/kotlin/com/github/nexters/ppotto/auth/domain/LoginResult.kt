package com.github.nexters.ppotto.auth.domain

data class LoginResult(
    val tokenPair: TokenPair,
    val isNewUser: Boolean,
    val pendingTerms: List<PendingTerm>,
)
