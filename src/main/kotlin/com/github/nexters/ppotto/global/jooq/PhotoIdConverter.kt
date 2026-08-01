package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.PhotoId
import org.jooq.Converter
import java.util.UUID

class PhotoIdConverter : Converter<UUID, PhotoId> {
    override fun from(databaseObject: UUID?): PhotoId? = databaseObject?.let(::PhotoId)

    override fun to(userObject: PhotoId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<PhotoId> = PhotoId::class.java
}
