package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.StickerId
import org.jooq.Converter
import java.util.UUID

class StickerIdConverter :
    Converter<UUID, StickerId> by Converter.ofNullable(
        UUID::class.java,
        StickerId::class.java,
        ::StickerId,
        StickerId::value,
    )
