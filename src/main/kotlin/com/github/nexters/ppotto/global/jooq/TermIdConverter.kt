package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.TermId
import org.jooq.Converter
import java.util.UUID

class TermIdConverter : Converter<UUID, TermId> {
    override fun from(databaseObject: UUID?): TermId? = databaseObject?.let(::TermId)

    override fun to(userObject: TermId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<TermId> = TermId::class.java
}
