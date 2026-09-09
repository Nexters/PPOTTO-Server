package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.byProvider
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import org.springframework.stereotype.Component

@Component
class OAuthSocialAccountRevoker(
    oauthClients: List<OAuthClient>,
) : SocialAccountRevoker {
    private val oauthClients = oauthClients.byProvider()

    override fun revoke(
        provider: OAuthProvider,
        providerRefreshToken: String,
    ) = checkNotNull(oauthClients[provider]) {
        "OAuth provider client가 연결되지 않았습니다."
    }.revoke(providerRefreshToken)
}
