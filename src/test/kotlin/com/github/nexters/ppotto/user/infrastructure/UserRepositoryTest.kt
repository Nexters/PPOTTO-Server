package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.application.port.ProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.domain.OAuthProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class UserRepositoryTest(
    userRepository: UserRepository,
    tokenCipher: ProviderRefreshTokenCipher,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("암호화된 제공자 토큰을 가진 소셜 계정을 저장하면") {
            val plaintext = "apple-refresh-token"
            val encrypted = tokenCipher.encrypt(plaintext)
            val saved =
                userRepository.save(
                    provider = OAuthProvider.APPLE,
                    providerUserId = "apple-user-${UUID.randomUUID()}",
                    email = "apple@example.com",
                    name = "애플사용자",
                    providerRefreshToken = encrypted,
                )

            When("소셜 계정과 저장된 원문을 조회하면") {
                val found = userRepository.findBySocialAccount(saved.provider, saved.providerUserId)
                val stored =
                    dslContext
                        .select(USERS.PROVIDER_REFRESH_TOKEN)
                        .from(USERS)
                        .where(USERS.ID.eq(saved.id))
                        .fetchOne(USERS.PROVIDER_REFRESH_TOKEN)

                Then("계정을 반환하고 토큰은 평문과 다르게 저장한다") {
                    found shouldBe saved
                    stored shouldNotBe plaintext
                    tokenCipher.decrypt(found!!.providerRefreshToken!!) shouldBe plaintext
                }
            }
        }

        Given("활성 사용자가 탈퇴하면") {
            val providerUserId = "withdraw-${UUID.randomUUID()}"
            val saved =
                userRepository.save(
                    provider = OAuthProvider.KAKAO,
                    providerUserId = providerUserId,
                    email = "before@example.com",
                    name = "탈퇴전사용자",
                )
            val withdrawnAt = Instant.now().minus(2, ChronoUnit.DAYS)

            When("익명화 상태를 저장하면") {
                val withdrawn = userRepository.withdraw(saved.withdraw(withdrawnAt))

                Then("활성 조회에서 제외하고 같은 소셜 계정으로 다시 가입할 수 있다") {
                    withdrawn?.email shouldBe "deleted+${saved.id}@users.invalid"
                    withdrawn?.name shouldBe "탈퇴한 사용자"
                    withdrawn?.providerRefreshToken.shouldBeNull()
                    userRepository.findById(saved.id).shouldBeNull()
                    userRepository.findBySocialAccount(OAuthProvider.KAKAO, providerUserId).shouldBeNull()

                    val recreated =
                        userRepository.save(
                            provider = OAuthProvider.KAKAO,
                            providerUserId = providerUserId,
                            email = "after@example.com",
                            name = "재가입사용자",
                        )
                    recreated.id shouldNotBe saved.id
                }
            }

            When("유예 기준보다 오래된 탈퇴 사용자를 조회하고 삭제하면") {
                userRepository.withdraw(saved.withdraw(withdrawnAt))
                val candidates = userRepository.findWithdrawnBefore(Instant.now().minus(1, ChronoUnit.DAYS), 10)
                val deleted = userRepository.hardDelete(saved.id)

                Then("탈퇴 사용자를 후보로 반환하고 하드 삭제한다") {
                    candidates.map { it.id } shouldContain saved.id
                    deleted shouldBe true
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = userRepository.findById(UserId(UUID.randomUUID()))

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }
    })
