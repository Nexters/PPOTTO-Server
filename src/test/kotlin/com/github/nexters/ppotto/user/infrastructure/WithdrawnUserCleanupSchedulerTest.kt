package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.support.CronExpression
import java.time.Instant

class WithdrawnUserCleanupSchedulerTest(
    applicationContext: ApplicationContext,
    properties: WithdrawnUserCleanupProperties,
    cleanupService: WithdrawnUserCleanupService,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("정리 배치 설정이 기본값일 때") {
            When("스케줄러 빈을 조회하면") {
                Then("비활성 기본값이라 실행 진입점을 등록하지 않는다") {
                    properties.enabled shouldBe false
                    applicationContext.getBeansOfType(WithdrawnUserCleanupScheduler::class.java).shouldBeEmpty()
                }
            }

            When("보존 정책 값을 확인하면") {
                Then("보수적인 기본 보존 기간과 파싱 가능한 cron을 사용한다") {
                    properties.retentionDays shouldBe 30
                    properties.batchSize shouldBe 100
                    CronExpression.isValidExpression(properties.cron) shouldBe true
                }
            }
        }

        Given("방금 탈퇴한 사용자가 있을 때") {
            val withdrawn =
                userRepository
                    .saveTestUser()
                    .let { userRepository.withdraw(it.withdraw(Instant.now()))!! }

            When("스케줄러를 직접 실행하면") {
                WithdrawnUserCleanupScheduler(cleanupService, properties).cleanup()

                Then("보존 기간이 지나지 않아 사용자 행을 남긴다") {
                    dslContext.fetchExists(USERS, USERS.ID.eq(withdrawn.id)) shouldBe true
                }
            }
        }
    })
