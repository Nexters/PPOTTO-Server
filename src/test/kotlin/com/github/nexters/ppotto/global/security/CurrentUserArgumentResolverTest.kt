package com.github.nexters.ppotto.global.security

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
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
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class CurrentUserArgumentResolverTest :
    BehaviorSpec({
        val resolver = CurrentUserArgumentResolver()
        val webRequest = ServletWebRequest(MockHttpServletRequest())
        val requiredParameter = methodParameter(CurrentUserArgumentFixture::required)
        val optionalParameter = methodParameter(CurrentUserArgumentFixture::optional)
        val rawRequiredParameter = methodParameter(CurrentUserArgumentFixture::rawRequired)

        afterEach {
            SecurityContextHolder.clearContext()
        }

        Given("강타입과 UUID 파라미터가 있으면") {
            When("지원 여부를 판정하면") {
                Then("둘 다 지원한다") {
                    resolver.supportsParameter(requiredParameter) shouldBe true
                    resolver.supportsParameter(optionalParameter) shouldBe true
                    resolver.supportsParameter(rawRequiredParameter) shouldBe true
                }
            }
        }

        Given("UUID principal이 인증 컨텍스트에 있으면") {
            val userId = UUID.randomUUID()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null)

            When("인증 사용자 인자를 해석하면") {
                Then("같은 UUID를 반환한다") {
                    resolver.resolveArgument(requiredParameter, null, webRequest, null) shouldBe userId
                    resolver.resolveArgument(optionalParameter, null, webRequest, null) shouldBe userId
                    resolver.resolveArgument(rawRequiredParameter, null, webRequest, null) shouldBe userId
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
        @AuthenticatedUser userId: UserId,
    ) = userId

    fun optional(
        @CurrentUser userId: UserId?,
    ) = userId

    fun rawRequired(
        @AuthenticatedUser userId: UUID,
    ) = userId
}

private fun methodParameter(function: KFunction<*>): MethodParameter =
    MethodParameter(function.javaMethod ?: error("자바 메서드를 찾을 수 없습니다: $function"), 0)
