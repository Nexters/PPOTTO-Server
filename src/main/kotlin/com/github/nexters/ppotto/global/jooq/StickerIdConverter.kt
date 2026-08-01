package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.StickerId
import org.jooq.Converter
import java.util.UUID

class StickerIdConverter : Converter<UUID, StickerId> {
    override fun from(databaseObject: UUID?): StickerId? = databaseObject?.let(::StickerId)

    override fun to(userObject: StickerId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<StickerId> = StickerId::class.java
}
