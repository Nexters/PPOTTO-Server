package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext

fun DSLContext.changeAnalysisStatus(
    analysisId: AnalysisId,
    status: AnalysisStatus,
) {
    update(ANALYSIS)
        .set(ANALYSIS.STATUS, status.name)
        .where(ANALYSIS.ID.eq(analysisId))
        .execute()
}
