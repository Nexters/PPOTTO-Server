package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.BoardId
import org.jooq.Converter
import java.util.UUID

class BoardIdConverter : Converter<UUID, BoardId> {
    override fun from(databaseObject: UUID?): BoardId? = databaseObject?.let(::BoardId)

    override fun to(userObject: BoardId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<BoardId> = BoardId::class.java
}
