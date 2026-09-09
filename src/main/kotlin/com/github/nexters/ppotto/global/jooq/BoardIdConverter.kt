package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.BoardId
import org.jooq.Converter
import java.util.UUID

class BoardIdConverter :
    Converter<UUID, BoardId> by Converter.ofNullable(
        UUID::class.java,
        BoardId::class.java,
        ::BoardId,
        BoardId::value,
    )
