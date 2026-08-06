package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class SocialUserRepository(
    private val dslContext: DSLContext,
) {
    fun saveIfAbsent(
        provider: OAuthProvider,
        providerUserId: String,
        email: String,
        name: String,
        providerRefreshToken: EncryptedProviderRefreshToken? = null,
    ): User? =
        dslContext
            .insertInto(
                USERS,
                USERS.PROVIDER,
                USERS.PROVIDER_USER_ID,
                USERS.EMAIL,
                USERS.NAME,
                USERS.PROVIDER_REFRESH_TOKEN,
            ).values(
                provider.toJooq(),
                providerUserId,
                email,
                name,
                providerRefreshToken?.value,
            ).onConflict(USERS.PROVIDER, USERS.PROVIDER_USER_ID)
            .where(USERS.DELETED_AT.isNull)
            .doNothing()
            .returning()
            .fetchOne()
            ?.toDomain()
}
