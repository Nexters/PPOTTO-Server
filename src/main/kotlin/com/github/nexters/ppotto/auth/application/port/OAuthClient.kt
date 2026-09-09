package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.global.oauth.OAuthProvider

interface OAuthClient {
    val provider: OAuthProvider

    fun authenticate(command: LoginCommand): SocialProfile

    fun revoke(providerRefreshToken: String)
}

fun List<OAuthClient>.byProvider(): Map<OAuthProvider, OAuthClient> {
    val clients = associateBy(OAuthClient::provider)
    check(clients.size == size) { "OAuth provider별 client는 하나만 등록할 수 있습니다." }
    return clients
}
