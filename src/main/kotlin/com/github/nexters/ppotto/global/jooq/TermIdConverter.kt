package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.TermId
import org.jooq.Converter
import java.util.UUID

class TermIdConverter :
    Converter<UUID, TermId> by Converter.ofNullable(
        UUID::class.java,
        TermId::class.java,
        ::TermId,
        TermId::value,
    )
