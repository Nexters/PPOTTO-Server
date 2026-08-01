package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.DrawingId
import org.jooq.Converter
import java.util.UUID

class DrawingIdConverter : Converter<UUID, DrawingId> {
    override fun from(databaseObject: UUID?): DrawingId? = databaseObject?.let(::DrawingId)

    override fun to(userObject: DrawingId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<DrawingId> = DrawingId::class.java
}
