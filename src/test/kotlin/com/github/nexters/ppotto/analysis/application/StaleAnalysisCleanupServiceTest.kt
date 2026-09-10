package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.photoUploadGroups
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val UPDATED_AT_TRIGGER = "analysis_set_updated_at"

class StaleAnalysisCleanupServiceTest(
    private val service: StaleAnalysisCleanupService,
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun createAnalysis(): AnalysisId {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            return analysisService
                .createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups(20)))
                .analysisId
        }

        fun ageTo(
            analysisId: AnalysisId,
            updatedAt: Instant,
            status: AnalysisStatus = AnalysisStatus.UPLOADING,
        ) {
            dslContext.execute("ALTER TABLE analysis DISABLE TRIGGER $UPDATED_AT_TRIGGER")
            try {
                dslContext
                    .update(ANALYSIS)
                    .set(ANALYSIS.STATUS, status.name)
                    .set(ANALYSIS.UPDATED_AT, updatedAt)
                    .where(ANALYSIS.ID.eq(analysisId))
                    .execute()
            } finally {
                dslContext.execute("ALTER TABLE analysis ENABLE TRIGGER $UPDATED_AT_TRIGGER")
            }
        }

        val hourAgo = Instant.now().minus(1, ChronoUnit.HOURS)

        Given("한 시간 넘게 UPLOADING에 멈춰 있는 분석이") {
            val analysisId = createAnalysis()
            ageTo(analysisId, Instant.now().minus(3, ChronoUnit.HOURS))

            When("만료 배치가 돌면") {
                val expired = service.cleanup(hourAgo, batchSize = 100)
                val analysis = analysisRepository.findById(analysisId)!!

                Then("만료된 분석 ID를 돌려준다") {
                    expired shouldContainExactly listOf(analysisId)
                }

                Then("FAILED(EXPIRED)로 마감된다") {
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe AnalysisRepository.FAILED_REASON_EXPIRED
                }

                Then("점유가 풀려 같은 사용자가 새 분석을 만들 수 있다") {
                    val board = boardRepository.save(analysis.userId)
                    analysisService.createAnalysis(analysis.userId, board.id, CreateAnalysisCommand(photoUploadGroups(20)))
                }

                Then("올라오지 않은 사진도 FAILED로 정리된다") {
                    photoRepository.findPendingByAnalysisId(analysisId).shouldBeEmpty()
                }
            }
        }

        Given("한 시간 넘게 ANALYZING에 멈춰 있는 분석이") {
            val analysisId = createAnalysis()
            ageTo(analysisId, Instant.now().minus(3, ChronoUnit.HOURS), AnalysisStatus.ANALYZING)

            When("만료 배치가 돌면") {
                val expired = service.cleanup(hourAgo, batchSize = 100)

                Then("파이프라인이 죽어 남은 행도 함께 만료된다") {
                    expired shouldContainExactly listOf(analysisId)
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                }
            }
        }

        Given("아직 기준 시간이 지나지 않은 분석이") {
            val analysisId = createAnalysis()
            ageTo(analysisId, Instant.now().minus(10, ChronoUnit.MINUTES))

            When("만료 배치가 돌면") {
                val expired = service.cleanup(hourAgo, batchSize = 100)

                Then("업로드 중인 분석은 건드리지 않는다") {
                    expired.shouldBeEmpty()
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.UPLOADING
                }
            }
        }

        Given("이미 끝난 오래된 분석이") {
            val analysisId = createAnalysis()
            ageTo(analysisId, Instant.now().minus(3, ChronoUnit.HOURS), AnalysisStatus.COMPLETED)

            When("만료 배치가 돌면") {
                val expired = service.cleanup(hourAgo, batchSize = 100)

                Then("완료된 분석은 되돌리지 않는다") {
                    expired.shouldBeEmpty()
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.COMPLETED
                }
            }
        }

        Given("멈춘 분석이 배치 크기보다 많을 때") {
            val analysisIds = List(3) { createAnalysis().also { id -> ageTo(id, Instant.now().minus(3, ChronoUnit.HOURS)) } }

            When("배치 크기 2로 만료 배치가 돌면") {
                val expired = service.cleanup(hourAgo, batchSize = 2)

                Then("한 번에 배치 크기만큼만 처리한다") {
                    expired.size shouldBe 2
                }

                Then("남은 분석은 다음 실행에서 처리된다") {
                    val remaining = service.cleanup(hourAgo, batchSize = 2)
                    (expired + remaining).toSet() shouldBe analysisIds.toSet()
                }
            }
        }

        Given("배치 크기가 0으로 주어졌을 때") {
            When("만료 배치가 돌면") {
                Then("설정 실수를 조용히 넘기지 않고 즉시 실패한다") {
                    shouldThrow<IllegalArgumentException> { service.cleanup(hourAgo, batchSize = 0) }
                }
            }
        }
    })
