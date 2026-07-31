package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.jooq.enums.OauthProvider
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class TermRepositoryTest(
    termRepository: TermRepository,
    termAgreementRepository: TermAgreementRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        fun saveTerm(
            code: String,
            version: String,
            effectiveAt: Instant,
            isRequired: Boolean = false,
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

        Given("같은 코드에 과거, 현재, 미래 약관 버전이 등록된 상태에서") {
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val tosCode = "TOS-${UUID.randomUUID()}"
            val privacyCode = "PRIVACY-${UUID.randomUUID()}"
            saveTerm(tosCode, "1.0", now.minusSeconds(3_600))
            val currentTos = saveTerm(tosCode, "2.0", now.minusSeconds(60))
            saveTerm(tosCode, "3.0", now.plusSeconds(3_600))
            val currentPrivacy = saveTerm(privacyCode, "1.0", now.minusSeconds(120))

            When("현재 유효 약관을 조회하면") {
                val found =
                    termRepository
                        .findCurrentEffective(now)
                        .filter { it.code == tosCode || it.code == privacyCode }

                Then("코드별 시행일이 가장 최근인 버전을 한 건씩 반환한다") {
                    found.map { it.id } shouldContainExactlyInAnyOrder
                        listOf(currentTos.id!!, currentPrivacy.id!!)
                }
            }
        }

        Given("사용자와 약관이 등록된 상태에서") {
            val userId =
                dslContext
                    .insertInto(USERS, USERS.PROVIDER, USERS.PROVIDER_USER_ID, USERS.EMAIL)
                    .values(
                        OauthProvider.KAKAO,
                        "term-repository-${UUID.randomUUID()}",
                        "term-repository-${UUID.randomUUID()}@example.com",
                    ).returning(USERS.ID)
                    .fetchOne(USERS.ID)!!
            val term =
                saveTerm(
                    code = "AGREEMENT-${UUID.randomUUID()}",
                    version = "1.0",
                    effectiveAt = Instant.now().minusSeconds(60),
                )

            When("같은 약관 동의를 중복해서 저장하면") {
                val firstSaved = termAgreementRepository.saveAll(userId, listOf(term.id!!, term.id!!))
                val secondSaved = termAgreementRepository.saveAll(userId, listOf(term.id!!))

                Then("동의 이력은 한 건만 생성되고 재요청은 무시된다") {
                    firstSaved.map { it.termId } shouldContainExactly listOf(term.id!!)
                    secondSaved shouldBe emptyList()
                    termAgreementRepository.findAgreedTermIds(userId, listOf(term.id!!)) shouldBe setOf(term.id!!)
                }
            }
        }
    })
