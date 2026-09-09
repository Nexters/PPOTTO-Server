package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.AnalysisRecord
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class AnalysisRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        userId: UserId,
        boardId: BoardId,
    ): Analysis =
        dslContext
            .insertInto(ANALYSIS, ANALYSIS.USER_ID, ANALYSIS.BOARD_ID, ANALYSIS.STATUS)
            .values(userId, boardId, AnalysisStatus.UPLOADING.name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: AnalysisId): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    fun findByIdAndUserId(
        id: AnalysisId,
        userId: UserId,
    ): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(id))
            .and(ANALYSIS.USER_ID.eq(userId))
            .fetchOne()
            ?.toDomain()

    fun findActiveByUserId(userId: UserId): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(userId))
            .and(ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }))
            .fetchOne()
            ?.toDomain()

    fun findByIdForUpdate(id: AnalysisId): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(id))
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun existsActiveByBoardIdAndUserId(
        boardId: BoardId,
        userId: UserId,
    ): Boolean =
        dslContext.fetchExists(
            dslContext
                .selectFrom(ANALYSIS)
                .where(ANALYSIS.BOARD_ID.eq(boardId))
                .and(ANALYSIS.USER_ID.eq(userId))
                .and(ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name })),
        )

    fun markAnalyzing(
        id: AnalysisId,
        startedAt: Instant,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
            .set(ANALYSIS.PROGRESS, ANALYZING_STARTED_PROGRESS)
            .set(ANALYSIS.STARTED_AT, startedAt)
            .where(ANALYSIS.ID.eq(id))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.UPLOADING.name))
            .execute()

    fun updateProgress(
        id: AnalysisId,
        progress: Int,
    ): Int {
        dslContext.execute("SET LOCAL lock_timeout = '$PROGRESS_LOCK_TIMEOUT'")
        return dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.PROGRESS, progress.coerceIn(MIN_PROGRESS, MAX_IN_PROGRESS))
            .where(ANALYSIS.ID.eq(id))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.ANALYZING.name))
            .execute()
    }

    fun markCompleted(
        id: AnalysisId,
        completedAt: Instant,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
            .set(ANALYSIS.PROGRESS, COMPLETED_PROGRESS)
            .set(ANALYSIS.COMPLETED_AT, completedAt)
            .where(ANALYSIS.ID.eq(id))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.ANALYZING.name))
            .execute()

    fun markFailed(
        id: AnalysisId,
        failedReason: String,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
            .set(ANALYSIS.FAILED_REASON, failedReason)
            .where(ANALYSIS.ID.eq(id))
            .and(ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }))
            .execute()

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

    companion object {
        const val FAILED_REASON_CANCELED = "CANCELED"

        private const val ANALYZING_STARTED_PROGRESS = 10
        private const val COMPLETED_PROGRESS = 100
        private const val PROGRESS_LOCK_TIMEOUT = "500ms"
        private const val MIN_PROGRESS = 0
        private const val MAX_IN_PROGRESS = 99
    }
}
