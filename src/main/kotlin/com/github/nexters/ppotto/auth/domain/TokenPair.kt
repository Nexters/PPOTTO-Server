package com.github.nexters.ppotto.auth.domain

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
)
