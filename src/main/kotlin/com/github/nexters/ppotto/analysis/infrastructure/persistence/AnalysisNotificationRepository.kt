package com.github.nexters.ppotto.analysis.infrastructure.persistence

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class AnalysisNotificationRepository(
    private val dslContext: DSLContext,
) {
    fun markRequested(
        analysisId: AnalysisId,
        requestedAt: Instant,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.NOTIFICATION_REQUESTED_AT, requestedAt)
            .where(ANALYSIS.ID.eq(analysisId))
            .and(ANALYSIS.NOTIFICATION_REQUESTED_AT.isNull)
            .execute()
}
