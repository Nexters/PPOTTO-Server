package com.github.nexters.ppotto.analysis.infrastructure.persistence

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class AnalysisCreatorRepository(
    private val dslContext: DSLContext,
) {
    fun findUserIdsByIds(ids: Collection<AnalysisId>): Map<AnalysisId, UserId> {
        if (ids.isEmpty()) return emptyMap()
        return dslContext
            .select(ANALYSIS.ID, ANALYSIS.USER_ID)
            .from(ANALYSIS)
            .where(ANALYSIS.ID.`in`(ids))
            .fetch()
            .associate { requireNotNull(it[ANALYSIS.ID]) to requireNotNull(it[ANALYSIS.USER_ID]) }
    }
}
