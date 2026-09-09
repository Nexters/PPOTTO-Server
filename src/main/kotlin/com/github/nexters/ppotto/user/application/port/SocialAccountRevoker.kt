package com.github.nexters.ppotto.user.application.port

import com.github.nexters.ppotto.global.oauth.OAuthProvider

fun interface SocialAccountRevoker {
    fun revoke(
        provider: OAuthProvider,
        providerRefreshToken: String,
    )
}
