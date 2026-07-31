package com.github.nexters.ppotto.global.security

import com.github.nexters.ppotto.global.error.UnauthorizedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest
import java.util.UUID

class CurrentUserArgumentResolverTest :
    BehaviorSpec({
        val resolver = CurrentUserArgumentResolver()
        val webRequest = ServletWebRequest(MockHttpServletRequest())
        val requiredParameter = methodParameter("required")
        val optionalParameter = methodParameter("optional")

        afterEach {
            SecurityContextHolder.clearContext()
        }

        Given("UUID principal이 인증 컨텍스트에 있으면") {
            val userId = UUID.randomUUID()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null)

            When("인증 사용자 인자를 해석하면") {
                Then("같은 UUID를 반환한다") {
                    resolver.resolveArgument(requiredParameter, null, webRequest, null) shouldBe userId
                    resolver.resolveArgument(optionalParameter, null, webRequest, null) shouldBe userId
                }
            }
        }

        Given("인증 principal이 없으면") {
            When("필수 인증 사용자 인자를 해석하면") {
                Then("COMMON-004 예외를 던진다") {
                    shouldThrow<UnauthorizedException> {
                        resolver.resolveArgument(requiredParameter, null, webRequest, null)
                    }
                }
            }

            When("선택 인증 사용자 인자를 해석하면") {
                Then("null을 반환한다") {
                    resolver.resolveArgument(optionalParameter, null, webRequest, null).shouldBeNull()
                }
            }
        }

        Given("UUID가 아닌 principal이 있으면") {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken("invalid", null)

            When("선택 인증 사용자 인자를 해석하면") {
                Then("COMMON-004 예외를 던진다") {
                    shouldThrow<UnauthorizedException> {
                        resolver.resolveArgument(optionalParameter, null, webRequest, null)
                    }
                }
            }
        }
    })

private class CurrentUserArgumentFixture {
    fun required(
        @AuthenticatedUser userId: UUID,
    ) = userId

    fun optional(
        @CurrentUser userId: UUID?,
    ) = userId
}

private fun methodParameter(methodName: String): MethodParameter =
    MethodParameter(
        CurrentUserArgumentFixture::class.java.getDeclaredMethod(methodName, UUID::class.java),
        0,
    )
