package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.jooq.enums.OauthProvider
import com.github.nexters.ppotto.jooq.tables.records.UsersRecord
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class UserRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        provider: OAuthProvider,
        providerUserId: String,
        email: String,
        providerRefreshToken: EncryptedProviderRefreshToken? = null,
    ): User =
        dslContext
            .insertInto(
                USERS,
                USERS.PROVIDER,
                USERS.PROVIDER_USER_ID,
                USERS.EMAIL,
                USERS.PROVIDER_REFRESH_TOKEN,
            ).values(
                provider.toJooq(),
                providerUserId,
                email,
                providerRefreshToken?.value,
            ).returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): User? =
        dslContext
            .selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .and(USERS.DELETED_AT.isNull)
            .and(USERS.PROVIDER.isNotNull)
            .and(USERS.PROVIDER_USER_ID.isNotNull)
            .and(USERS.EMAIL.isNotNull)
            .fetchOne()
            ?.toDomain()

    fun findBySocialAccount(
        provider: OAuthProvider,
        providerUserId: String,
    ): User? =
        dslContext
            .selectFrom(USERS)
            .where(USERS.PROVIDER.eq(provider.toJooq()))
            .and(USERS.PROVIDER_USER_ID.eq(providerUserId))
            .and(USERS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun updateSocialProfile(
        id: UUID,
        email: String,
        providerRefreshToken: EncryptedProviderRefreshToken?,
    ): User? =
        dslContext
            .update(USERS)
            .set(USERS.EMAIL, email)
            .apply {
                providerRefreshToken?.let {
                    set(USERS.PROVIDER_REFRESH_TOKEN, it.value)
                }
            }.where(USERS.ID.eq(id))
            .and(USERS.DELETED_AT.isNull)
            .returning()
            .fetchOne()
            ?.toDomain()

    fun withdraw(user: User): User? =
        dslContext
            .update(USERS)
            .set(USERS.EMAIL, user.email)
            .setNull(USERS.PROVIDER_REFRESH_TOKEN)
            .set(USERS.DELETED_AT, user.deletedAt)
            .where(USERS.ID.eq(user.id))
            .and(USERS.DELETED_AT.isNull)
            .returning()
            .fetchOne()
            ?.toDomain()

    fun findWithdrawnBefore(
        deletedBefore: Instant,
        limit: Int,
    ): List<User> =
        dslContext
            .selectFrom(USERS)
            .where(USERS.DELETED_AT.isNotNull)
            .and(USERS.DELETED_AT.le(deletedBefore))
            .orderBy(USERS.DELETED_AT, USERS.ID)
            .limit(limit)
            .fetch()
            .map { it.toDomain() }

    fun hardDelete(id: UUID): Boolean =
        dslContext
            .deleteFrom(USERS)
            .where(USERS.ID.eq(id))
            .and(USERS.DELETED_AT.isNotNull)
            .execute() == 1
}

internal fun UsersRecord.toDomain() =
    User(
        id = id!!,
        provider = provider!!.toDomain(),
        providerUserId = providerUserId!!,
        email = email!!,
        providerRefreshToken = providerRefreshToken?.let(::EncryptedProviderRefreshToken),
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        deletedAt = deletedAt,
    )

internal fun OAuthProvider.toJooq(): OauthProvider = OauthProvider.valueOf(name)

internal fun OauthProvider.toDomain(): OAuthProvider = OAuthProvider.valueOf(name)
