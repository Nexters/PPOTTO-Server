package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.support.ResettableFake
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import java.util.concurrent.CopyOnWriteArrayList

class FakeSocialAccountRevoker :
    SocialAccountRevoker,
    ResettableFake {
    val revocations = CopyOnWriteArrayList<Revocation>()

    var failure: Throwable? = null

    override fun revoke(
        provider: OAuthProvider,
        providerRefreshToken: String,
    ) {
        failure?.let { throw it }
        revocations += Revocation(provider, providerRefreshToken)
    }

    override fun reset() {
        revocations.clear()
        failure = null
    }

    fun clear() = reset()
}

data class Revocation(
    val provider: OAuthProvider,
    val providerRefreshToken: String,
)
