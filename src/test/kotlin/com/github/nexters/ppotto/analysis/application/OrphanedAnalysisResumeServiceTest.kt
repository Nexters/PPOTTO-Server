package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val UPDATED_AT_TRIGGER = "analysis_set_updated_at"

class OrphanedAnalysisResumeServiceTest(
    private val orphanedAnalysisResumeService: OrphanedAnalysisResumeService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val stickerRepository: StickerRepository,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun analyzingAnalysis(uploadCompleted: Boolean = true): AnalysisId {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            if (uploadCompleted) photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            return analysis.id
        }

        Given("재시작으로 파이프라인이 끊겨 ANALYZING 으로 남은 분석이") {
            val analysisId = analyzingAnalysis()

            When("애플리케이션이 다시 뜨면") {
                orphanedAnalysisResumeService.resumeOrphanedAnalyses()

                Then("만료를 기다리지 않고 그 자리에서 다시 돌아 COMPLETED 로 마감된다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.COMPLETED
                    stickerRepository.findAllByAnalysisId(analysisId) shouldHaveSize 1
                }
            }
        }

        Given("업로드가 끝난 사진이 하나도 없는 ANALYZING 분석이") {
            val analysisId = analyzingAnalysis(uploadCompleted = false)

            When("애플리케이션이 다시 뜨면") {
                orphanedAnalysisResumeService.resumeOrphanedAnalyses()

                Then("재개할 수 없다는 사유로 즉시 FAILED 로 마감한다") {
                    val analysis = analysisRepository.findById(analysisId)!!
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe AnalysisRepository.FAILED_REASON_NOT_RESUMABLE
                }
            }
        }

        Given("만료 시한을 이미 넘긴 ANALYZING 분석이") {
            val analysisId = analyzingAnalysis()
            dslContext.execute("ALTER TABLE analysis DISABLE TRIGGER $UPDATED_AT_TRIGGER")
            try {
                dslContext
                    .update(ANALYSIS)
                    .set(ANALYSIS.UPDATED_AT, Instant.now().minus(1, ChronoUnit.DAYS))
                    .where(ANALYSIS.ID.eq(analysisId))
                    .execute()
            } finally {
                dslContext.execute("ALTER TABLE analysis ENABLE TRIGGER $UPDATED_AT_TRIGGER")
            }

            When("애플리케이션이 다시 뜨면") {
                orphanedAnalysisResumeService.resumeOrphanedAnalyses()

                Then("재개하지 않고 만료 배치에 맡긴다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.ANALYZING
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                }
            }
        }
    })
