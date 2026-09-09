package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.terms.support.saveTerm
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class TermRepositoryTest(
    termRepository: TermRepository,
    termAgreementRepository: TermAgreementRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("같은 코드에 과거, 현재, 미래 약관 버전이 등록된 상태에서") {
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val tosCode = "TOS-${UUID.randomUUID()}"
            val privacyCode = "PRIVACY-${UUID.randomUUID()}"
            dslContext.saveTerm(tosCode, "1.0", now.minusSeconds(3_600))
            val currentTos = dslContext.saveTerm(tosCode, "2.0", now.minusSeconds(60))
            dslContext.saveTerm(tosCode, "3.0", now.plusSeconds(3_600))
            val currentPrivacy = dslContext.saveTerm(privacyCode, "1.0", now.minusSeconds(120))

            When("현재 유효 약관을 조회하면") {
                val found =
                    termRepository
                        .findCurrentEffective(now)
                        .filter { it.code == tosCode || it.code == privacyCode }

                Then("코드별 시행일이 가장 최근인 버전을 한 건씩 반환한다") {
                    found.map { it.id } shouldContainExactlyInAnyOrder listOf(currentTos.id, currentPrivacy.id)
                }
            }
        }

        Given("사용자와 약관이 등록된 상태에서") {
            val userId = userRepository.saveTestUser().id
            val term = dslContext.saveTerm("AGREEMENT-${UUID.randomUUID()}")

            When("같은 약관 동의를 중복해서 저장하면") {
                val firstSaved = termAgreementRepository.saveAll(userId, listOf(term.id, term.id))
                val secondSaved = termAgreementRepository.saveAll(userId, listOf(term.id))

                Then("첫 요청만 한 건을 저장하고 재요청은 무시된다") {
                    firstSaved shouldBe 1
                    secondSaved shouldBe 0
                }

                Then("동의한 약관으로 조회된다") {
                    termAgreementRepository.findAgreedTermIds(userId, listOf(term.id)) shouldBe setOf(term.id)
                }
            }
        }
    })
