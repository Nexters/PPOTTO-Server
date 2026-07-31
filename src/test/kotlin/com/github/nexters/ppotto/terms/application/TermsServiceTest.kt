package com.github.nexters.ppotto.terms.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.jooq.enums.OauthProvider
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.util.UUID

class TermsServiceTest(
    termsService: TermsService,
    dslContext: DSLContext,
) : IntegrationTest({
        fun saveUser(): UUID =
            dslContext
                .insertInto(USERS, USERS.PROVIDER, USERS.PROVIDER_USER_ID, USERS.EMAIL)
                .values(
                    OauthProvider.KAKAO,
                    "terms-service-${UUID.randomUUID()}",
                    "terms-service-${UUID.randomUUID()}@example.com",
                ).returning(USERS.ID)
                .fetchOne(USERS.ID)!!

        fun saveTerm(
            code: String,
            version: String,
            effectiveAt: Instant,
            isRequired: Boolean,
        ) = dslContext
            .insertInto(
                TERMS,
                TERMS.CODE,
                TERMS.VERSION,
                TERMS.IS_REQUIRED,
                TERMS.CONTENT_URL,
                TERMS.EFFECTIVE_AT,
            ).values(code, version, isRequired, "https://example.com/$code/$version", effectiveAt)
            .returning()
            .fetchOne()!!

        Given("같은 코드의 과거 버전에는 동의했고 현재 버전에는 동의하지 않은 사용자가") {
            val userId = saveUser()
            val code = "STATUS-${UUID.randomUUID()}"
            val oldTerm = saveTerm(code, "1.0", Instant.now().minusSeconds(7_200), false)
            val currentTerm = saveTerm(code, "2.0", Instant.now().minusSeconds(3_600), false)
            dslContext
                .insertInto(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                .values(userId, oldTerm.id!!)
                .execute()

            When("현재 약관과 미동의 약관을 조회하면") {
                val current =
                    termsService
                        .findCurrentTerms(userId)
                        .filter { it.code == code }
                val pending =
                    termsService
                        .findPendingTerms(userId)
                        .filter { it.code == code }

                Then("현재 버전은 미동의 상태이며 미동의 목록에 포함된다") {
                    current.map { it.id to it.agreed } shouldContainExactly listOf(currentTerm.id!! to false)
                    pending.map { it.id } shouldContainExactly listOf(currentTerm.id!!)
                }
            }
        }

        Given("현재 필수 약관과 선택 약관이 있는 상태에서") {
            val userId = saveUser()
            val requiredTerm =
                saveTerm(
                    code = "REQUIRED-${UUID.randomUUID()}",
                    version = "1.0",
                    effectiveAt = Instant.now().minusSeconds(60),
                    isRequired = true,
                )
            val optionalTerm =
                saveTerm(
                    code = "OPTIONAL-${UUID.randomUUID()}",
                    version = "1.0",
                    effectiveAt = Instant.now().minusSeconds(60),
                    isRequired = false,
                )

            When("필수 약관 없이 동의를 요청하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        termsService.agree(userId, listOf(optionalTerm.id!!))
                    }

                Then("TERM-001 오류가 발생하고 동의 이력이 저장되지 않는다") {
                    exception.errorCode.code shouldBe "TERM-001"
                    dslContext
                        .fetchCount(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(userId)) shouldBe 0
                }
            }

            When("필수 약관을 포함해 같은 동의를 반복 요청한 뒤 선택 약관만 추가 동의하면") {
                val requiredTermIds =
                    termsService
                        .findCurrentTerms(userId)
                        .filter { it.isRequired }
                        .map { it.id }
                termsService.agree(userId, requiredTermIds)
                termsService.agree(userId, requiredTermIds)
                termsService.agree(userId, listOf(optionalTerm.id!!))

                Then("기존 필수 동의를 인정하고 각 약관 동의 이력을 한 건씩만 저장한다") {
                    dslContext
                        .select(TERM_AGREEMENTS.TERM_ID)
                        .from(TERM_AGREEMENTS)
                        .where(TERM_AGREEMENTS.USER_ID.eq(userId))
                        .fetch(TERM_AGREEMENTS.TERM_ID)
                        .filterNotNull()
                        .filter { it == requiredTerm.id || it == optionalTerm.id } shouldContainExactlyInAnyOrder
                        listOf(requiredTerm.id!!, optionalTerm.id!!)
                }
            }
        }

        Given("현재 유효하지 않은 약관 아이디가 요청에 포함된 상태에서") {
            val userId = saveUser()
            saveTerm(
                code = "CURRENT-${UUID.randomUUID()}",
                version = "1.0",
                effectiveAt = Instant.now().minusSeconds(60),
                isRequired = true,
            )
            val futureTerm =
                saveTerm(
                    code = "FUTURE-${UUID.randomUUID()}",
                    version = "1.0",
                    effectiveAt = Instant.now().plusSeconds(3_600),
                    isRequired = false,
                )

            When("동의를 요청하면") {
                val currentRequiredTermIds =
                    termsService
                        .findCurrentTerms(userId)
                        .filter { it.isRequired }
                        .map { it.id }
                termsService.agree(userId, currentRequiredTermIds)

                Then("잘못된 입력 오류가 발생한다") {
                    shouldThrow<InvalidInputException> {
                        termsService.agree(userId, currentRequiredTermIds + futureTerm.id!!)
                    }.errorCode.code shouldBe "COMMON-001"
                }
            }
        }
    })
