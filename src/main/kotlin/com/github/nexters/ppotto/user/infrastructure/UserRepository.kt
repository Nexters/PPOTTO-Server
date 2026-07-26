package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.jooq.tables.records.UsersRecord
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.user.domain.User
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserRepository(
    private val dslContext: DSLContext,
) {
    fun save(): User =
        dslContext
            .insertInto(USERS)
            .defaultValues()
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): User? =
        dslContext
            .selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    private fun UsersRecord.toDomain() =
        User(
            id = id!!,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
