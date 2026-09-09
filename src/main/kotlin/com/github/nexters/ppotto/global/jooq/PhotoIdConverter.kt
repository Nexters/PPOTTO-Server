package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.PhotoId
import org.jooq.Converter
import java.util.UUID

class PhotoIdConverter :
    Converter<UUID, PhotoId> by Converter.ofNullable(
        UUID::class.java,
        PhotoId::class.java,
        ::PhotoId,
        PhotoId::value,
    )
