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
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
            .values(UserId(userId), BoardId(boardId), AnalysisStatus.UPLOADING.name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .fetchOne()
            ?.toDomain()

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .and(ANALYSIS.USER_ID.eq(UserId(userId)))
            .fetchOne()
            ?.toDomain()

    fun findActiveByUserId(userId: UUID): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.USER_ID.eq(UserId(userId)))
            .and(
                ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }),
            ).orderBy(ANALYSIS.CREATED_AT.desc())
            .limit(1)
            .fetchOne()
            ?.toDomain()

    fun findByIdForUpdate(id: UUID): Analysis? =
        dslContext
            .selectFrom(ANALYSIS)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun existsActiveByBoardIdAndUserId(
        boardId: UUID,
        userId: UUID,
    ): Boolean =
        dslContext.fetchExists(
            dslContext
                .selectFrom(ANALYSIS)
                .where(ANALYSIS.BOARD_ID.eq(BoardId(boardId)))
                .and(ANALYSIS.USER_ID.eq(UserId(userId)))
                .and(
                    ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }),
                ),
        )

    fun markAnalyzing(
        id: UUID,
        startedAt: Instant,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
            .set(ANALYSIS.PROGRESS, ANALYZING_STARTED_PROGRESS)
            .set(ANALYSIS.STARTED_AT, startedAt)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.UPLOADING.name))
            .execute()

    // 병렬 파이프라인 워커/진행률 티커 등 Spring 트랜잭션 컨텍스트가 없는 스레드에서도 호출되므로,
    // 호출 스레드와 무관하게 매번 독립적인 트랜잭션을 열고 커밋하도록 명시적으로 @Transactional을 붙인다.
    // 이게 없으면 새 스레드에서 실행된 UPDATE가 커밋되지 않고 idle in transaction 상태로 남아
    // 해당 행의 락을 영구히 들고 있는 채로 멈추는 문제가 있었다.
    @Transactional
    fun updateProgress(
        id: UUID,
        progress: Int,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.PROGRESS, progress.coerceIn(MIN_PROGRESS, MAX_IN_PROGRESS))
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.ANALYZING.name))
            .execute()

    fun markCompleted(
        id: UUID,
        completedAt: Instant,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
            .set(ANALYSIS.PROGRESS, COMPLETED_PROGRESS)
            .set(ANALYSIS.COMPLETED_AT, completedAt)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .and(ANALYSIS.STATUS.eq(AnalysisStatus.ANALYZING.name))
            .execute()

    fun markFailed(
        id: UUID,
        failedReason: String,
    ): Int =
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
            .set(ANALYSIS.FAILED_REASON, failedReason)
            .where(ANALYSIS.ID.eq(AnalysisId(id)))
            .and(ANALYSIS.STATUS.`in`(AnalysisStatus.ACTIVE.map { it.name }))
            .execute()

    private fun AnalysisRecord.toDomain() =
        Analysis(
            id = id!!.value,
            userId = userId.value,
            boardId = boardId.value,
            status = AnalysisStatus.valueOf(status),
            progress = progress!!,
            failedReason = failedReason,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )

    companion object {
        const val ANALYZING_STARTED_PROGRESS = 10
        const val COMPLETED_PROGRESS = 100
        const val FAILED_REASON_CANCELED = "CANCELED"
        private const val MIN_PROGRESS = 0
        private const val MAX_IN_PROGRESS = 99
    }
}
