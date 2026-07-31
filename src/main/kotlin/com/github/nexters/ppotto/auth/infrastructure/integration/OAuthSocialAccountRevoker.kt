package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import org.springframework.stereotype.Component
import com.github.nexters.ppotto.user.domain.OAuthProvider as UserOAuthProvider

@Component
class OAuthSocialAccountRevoker(
    oauthClients: List<OAuthClient>,
) : SocialAccountRevoker {
    private val oauthClients =
        oauthClients.associateBy(OAuthClient::provider).also {
            check(it.size == oauthClients.size) { "OAuth provider별 client는 하나만 등록할 수 있습니다." }
        }

    override fun revoke(
        provider: UserOAuthProvider,
        providerRefreshToken: String,
    ) = checkNotNull(oauthClients[provider.toAuthProvider()]) {
        "OAuth provider client가 연결되지 않았습니다."
    }.revoke(providerRefreshToken)

    private fun UserOAuthProvider.toAuthProvider(): OAuthProvider =
        when (this) {
            UserOAuthProvider.KAKAO -> OAuthProvider.KAKAO
            UserOAuthProvider.APPLE -> OAuthProvider.APPLE
        }
}
