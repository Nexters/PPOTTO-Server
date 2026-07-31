package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.LoginResult
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.TokenPair
import java.util.UUID

data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
) {
    companion object {
        fun from(tokenPair: TokenPair): TokenPairResponse =
            TokenPairResponse(tokenPair.accessToken, tokenPair.refreshToken, tokenPair.accessTokenExpiresIn)
    }
}

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
    val isNewUser: Boolean,
    val pendingTerms: List<PendingTermResponse>,
) {
    companion object {
        fun from(result: LoginResult): LoginResponse =
            LoginResponse(
                accessToken = result.tokenPair.accessToken,
                refreshToken = result.tokenPair.refreshToken,
                accessTokenExpiresIn = result.tokenPair.accessTokenExpiresIn,
                isNewUser = result.isNewUser,
                pendingTerms = result.pendingTerms.map(PendingTermResponse::from),
            )
    }
}

data class PendingTermResponse(
    val id: UUID,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val agreed: Boolean,
) {
    companion object {
        fun from(term: PendingTerm): PendingTermResponse =
            PendingTermResponse(term.id, term.code, term.version, term.isRequired, term.contentUrl, term.agreed)
    }
}
