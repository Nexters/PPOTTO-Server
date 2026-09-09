package com.github.nexters.ppotto.terms.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.terms.support.saveTerm
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.util.UUID

class TermsServiceTest(
    termsService: TermsService,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("같은 코드의 과거 버전에는 동의했고 현재 버전에는 동의하지 않은 사용자가") {
            val userId = userRepository.saveTestUser().id
            val code = "STATUS-${UUID.randomUUID()}"
            val oldTerm = dslContext.saveTerm(code, "1.0", Instant.now().minusSeconds(7_200))
            val currentTerm = dslContext.saveTerm(code, "2.0", Instant.now().minusSeconds(3_600))
            dslContext
                .insertInto(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                .values(userId, oldTerm.id)
                .execute()

            When("현재 약관과 미동의 약관을 조회하면") {
                val current = termsService.findCurrentTerms(userId).filter { it.code == code }
                val pending = termsService.findPendingTerms(userId).filter { it.code == code }

                Then("현재 버전만 미동의 상태로 반환한다") {
                    current.map { it.id to it.agreed } shouldContainExactly listOf(currentTerm.id to false)
                }

                Then("현재 버전이 미동의 목록에 포함된다") {
                    pending.map { it.id } shouldContainExactly listOf(currentTerm.id)
                }
            }
        }

        Given("현재 필수 약관과 선택 약관이 있는 상태에서") {
            val userId = userRepository.saveTestUser().id
            dslContext.saveTerm("REQUIRED-${UUID.randomUUID()}", isRequired = true)
            val optionalTerm = dslContext.saveTerm("OPTIONAL-${UUID.randomUUID()}")

            When("필수 약관 없이 동의를 요청하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        termsService.agree(userId, listOf(optionalTerm.id))
                    }

                Then("TERM-001 오류가 발생한다") {
                    exception.errorCode.code shouldBe "TERM-001"
                }

                Then("동의 이력이 저장되지 않는다") {
                    dslContext.fetchCount(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(userId)) shouldBe 0
                }
            }
        }

        Given("필수 약관에 이미 동의한 사용자가") {
            val userId = userRepository.saveTestUser().id
            val requiredTerm = dslContext.saveTerm("AGREED-REQUIRED-${UUID.randomUUID()}", isRequired = true)
            val optionalTerm = dslContext.saveTerm("AGREED-OPTIONAL-${UUID.randomUUID()}")
            val requiredTermIds =
                termsService
                    .findCurrentTerms(userId)
                    .filter { it.isRequired }
                    .map { it.id }
            termsService.agree(userId, requiredTermIds)
            termsService.agree(userId, requiredTermIds)

            When("선택 약관만 추가로 동의하면") {
                termsService.agree(userId, listOf(optionalTerm.id))

                Then("기존 필수 동의를 인정하고 각 약관 동의 이력을 한 건씩만 저장한다") {
                    dslContext
                        .select(TERM_AGREEMENTS.TERM_ID)
                        .from(TERM_AGREEMENTS)
                        .where(TERM_AGREEMENTS.USER_ID.eq(userId))
                        .fetch(TERM_AGREEMENTS.TERM_ID)
                        .filterNotNull()
                        .filter { it == requiredTerm.id || it == optionalTerm.id } shouldContainExactlyInAnyOrder
                        listOf(requiredTerm.id, optionalTerm.id)
                }
            }
        }

        Given("현재 유효하지 않은 약관 아이디가 요청에 포함된 상태에서") {
            val userId = userRepository.saveTestUser().id
            dslContext.saveTerm("CURRENT-${UUID.randomUUID()}", isRequired = true)
            val futureTerm =
                dslContext.saveTerm(
                    code = "FUTURE-${UUID.randomUUID()}",
                    effectiveAt = Instant.now().plusSeconds(3_600),
                )
            val currentRequiredTermIds =
                termsService
                    .findCurrentTerms(userId)
                    .filter { it.isRequired }
                    .map { it.id }
            termsService.agree(userId, currentRequiredTermIds)

            When("아직 시행되지 않은 약관까지 포함해 동의를 요청하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        termsService.agree(userId, currentRequiredTermIds + futureTerm.id)
                    }

                Then("COMMON-001 오류가 발생한다") {
                    exception.errorCode.code shouldBe "COMMON-001"
                }
            }
        }
    })
