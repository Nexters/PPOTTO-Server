package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AnalysisWithdrawalRepository(
    private val dslContext: DSLContext,
) {
    fun findAllIdsByUserId(userId: UUID): List<UUID> =
        dslContext
            .select(ANALYSIS.ID)
            .from(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(userId))
            .orderBy(ANALYSIS.ID)
            .fetch(ANALYSIS.ID)
            .filterNotNull()

    fun hardDeleteAllByUserId(userId: UUID): Int =
        dslContext
            .deleteFrom(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(userId))
            .execute()
}
