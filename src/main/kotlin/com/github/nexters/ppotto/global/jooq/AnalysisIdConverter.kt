package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.jooq.Converter
import java.util.UUID

class AnalysisIdConverter : Converter<UUID, AnalysisId> {
    override fun from(databaseObject: UUID?): AnalysisId? = databaseObject?.let(::AnalysisId)

    override fun to(userObject: AnalysisId?): UUID? = userObject?.value

    override fun fromType(): Class<UUID> = UUID::class.java

    override fun toType(): Class<AnalysisId> = AnalysisId::class.java
}
