package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.jooq.Converter
import java.util.UUID

class AnalysisIdConverter :
    Converter<UUID, AnalysisId> by Converter.ofNullable(
        UUID::class.java,
        AnalysisId::class.java,
        ::AnalysisId,
        AnalysisId::value,
    )
