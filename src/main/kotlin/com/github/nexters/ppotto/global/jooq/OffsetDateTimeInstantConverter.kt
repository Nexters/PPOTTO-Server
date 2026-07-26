package com.github.nexters.ppotto.global.jooq

import org.jooq.Converter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeInstantConverter : Converter<OffsetDateTime, Instant> {
    override fun from(databaseObject: OffsetDateTime?): Instant? = databaseObject?.toInstant()

    override fun to(userObject: Instant?): OffsetDateTime? = userObject?.atOffset(ZoneOffset.UTC)

    override fun fromType(): Class<OffsetDateTime> = OffsetDateTime::class.java

    override fun toType(): Class<Instant> = Instant::class.java
}
