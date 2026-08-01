package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.UserId
import org.jooq.Converter
import java.util.UUID

class UserIdConverter : Converter<UUID, UserId> {
    override fun from(databaseObject: UUID?): UserId? = databaseObject?.let(::UserId)

    override fun to(userObject: UserId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<UserId> = UserId::class.java
}
