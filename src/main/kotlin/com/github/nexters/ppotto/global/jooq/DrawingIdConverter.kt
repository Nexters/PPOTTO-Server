package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.DrawingId
import org.jooq.Converter
import java.util.UUID

class DrawingIdConverter :
    Converter<UUID, DrawingId> by Converter.ofNullable(
        UUID::class.java,
        DrawingId::class.java,
        ::DrawingId,
        DrawingId::value,
    )
