package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class SentryUserContextProviderTest :
    BehaviorSpec({
        val provider = SentryUserContextProvider()

        afterEach {
            SecurityContextHolder.clearContext()
        }

        Given("UUID principal이 인증 컨텍스트에 있으면") {
            val userId = UUID.randomUUID()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null)

            When("Sentry 유저 컨텍스트를 만들면") {
                val user = provider.provideUser()

                Then("userId만 담고 이메일은 비운다") {
                    user?.id shouldBe userId.toString()
                    user?.email.shouldBeNull()
                    user?.username.shouldBeNull()
                    user?.ipAddress.shouldBeNull()
                }
            }
        }

        Given("인증 정보가 없으면") {
            When("Sentry 유저 컨텍스트를 만들면") {
                Then("null을 반환한다") {
                    provider.provideUser().shouldBeNull()
                }
            }
        }

        Given("익명 인증이면") {
            SecurityContextHolder.getContext().authentication =
                AnonymousAuthenticationToken(
                    "key",
                    "anonymousUser",
                    listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
                )

            When("Sentry 유저 컨텍스트를 만들면") {
                Then("null을 반환한다") {
                    provider.provideUser().shouldBeNull()
                }
            }
        }

        Given("UUID가 아닌 principal이면") {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken("swagger-basic-user", null)

            When("Sentry 유저 컨텍스트를 만들면") {
                Then("null을 반환한다") {
                    provider.provideUser().shouldBeNull()
                }
            }
        }
    })
