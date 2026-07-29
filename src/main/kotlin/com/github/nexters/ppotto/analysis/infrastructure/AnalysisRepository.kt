package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.jooq.tables.records.AnalysisRecord
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AnalysisRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        userId: UUID,
        boardId: UUID,
    ): Analysis =
        dslContext
            .insertInto(ANALYSIS, ANALYSIS.USER_ID, ANALYSIS.BOARD_ID, ANALYSIS.STATUS)
            .values(userId, boardId, AnalysisStatus.UPLOADING.name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    fun findByIdForUpdate(id: UUID): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(id))
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    private fun AnalysisRecord.toDomain() =
        Analysis(
            id = id!!,
            userId = userId,
            boardId = boardId,
            status = AnalysisStatus.valueOf(status),
            progress = progress!!,
            failedReason = failedReason,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
