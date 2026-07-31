package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.domain.OAuthProvider

class FakeSocialAccountRevoker : SocialAccountRevoker {
    val revocations = mutableListOf<Revocation>()

    override fun revoke(
        provider: OAuthProvider,
        providerRefreshToken: String,
    ) {
        revocations += Revocation(provider, providerRefreshToken)
    }

    fun clear() {
        revocations.clear()
    }
}

data class Revocation(
    val provider: OAuthProvider,
    val providerRefreshToken: String,
)
