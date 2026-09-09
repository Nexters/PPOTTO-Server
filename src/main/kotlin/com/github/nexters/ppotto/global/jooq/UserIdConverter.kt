package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.UserId
import org.jooq.Converter
import java.util.UUID

class UserIdConverter :
    Converter<UUID, UserId> by Converter.ofNullable(
        UUID::class.java,
        UserId::class.java,
        ::UserId,
        UserId::value,
    )
