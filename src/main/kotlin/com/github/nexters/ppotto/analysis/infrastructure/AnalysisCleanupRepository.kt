package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class AnalysisCleanupRepository(
    private val dslContext: DSLContext,
) {
    fun findStaleActiveIds(
        updatedBefore: Instant,
        limit: Int,
    ): List<AnalysisId> =
        dslContext
            .select(ANALYSIS.ID)
            .from(ANALYSIS)
            .where(ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }))
            .and(ANALYSIS.UPDATED_AT.lt(updatedBefore))
            .orderBy(ANALYSIS.ID.asc())
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .map { it.value1()!! }
}
