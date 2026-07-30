package com.github.nexters.ppotto.user.application.port

import com.github.nexters.ppotto.user.domain.OAuthProvider

fun interface SocialAccountRevoker {
    fun revoke(
        provider: OAuthProvider,
        providerRefreshToken: String,
    )
}
