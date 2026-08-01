package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class AnalysisWithdrawalRepository(
    private val dslContext: DSLContext,
) {
    fun findAllIdsByUserId(userId: UserId): List<AnalysisId> =
        dslContext
            .select(ANALYSIS.ID)
            .from(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(userId))
            .orderBy(ANALYSIS.ID)
            .fetch(ANALYSIS.ID)
            .filterNotNull()

    fun hardDeleteAllByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(userId))
            .execute()
}
