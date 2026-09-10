package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.infrastructure.security.BearerTokenAuthenticationFilter
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
@ActiveProfiles("test", "secured")
class AuthSecurityChainTest(
    @Autowired val mockMvc: MockMvc,
    tokenProvider: TokenProvider,
    userRepository: UserRepository,
    applicationContext: ApplicationContext,
) : IntegrationTest({
        Given("프로덕션과 같은 Bearer 인증 체인이 활성화된 상태에서") {
            When("익명으로 보호된 보드 목록을 조회하면") {
                val response = mockMvc.perform(get("/boards"))

                Then("401과 COMMON-004 봉투를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("익명으로 로그인을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"provider":"KAKAO"}"""),
                    )

                Then("permitAll 경로라 401이 아니라 요청 검증 결과인 400을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("익명으로 약관 목록을 조회하면") {
                val response = mockMvc.perform(get("/terms"))

                Then("선택 인증 경로라 200을 반환한다") {
                    response.andExpect(status().isOk)
                }
            }

            When("익명으로 스티커 상세를 조회하면") {
                val response = mockMvc.perform(get("/stickers/${UUID.randomUUID()}"))

                Then("보호된 경로라 401과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("익명으로 없는 공유 토큰의 리캡을 조회하면") {
                val response = mockMvc.perform(get("/stickers/shared/${UUID.randomUUID()}"))

                Then("선택 인증 경로라 401이 아니라 404를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }
        }

        Given("유효한 access token을 가진 사용자가 있을 때") {
            val user = userRepository.saveTestUser()
            val accessToken = tokenProvider.issue(user.id).accessToken

            When("Bearer 헤더로 보드 목록을 조회하면") {
                val response = mockMvc.perform(get("/boards").header("Authorization", "Bearer $accessToken"))

                Then("200과 빈 보드 목록을 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data", hasSize<Any>(0)))
                }
            }
        }

        Given("Bearer 인증 필터가 빈으로 등록된 상태에서") {
            When("서블릿 필터 등록 빈을 조회하면") {
                val registrations =
                    applicationContext
                        .getBeansOfType(FilterRegistrationBean::class.java)
                        .values
                        .filter { it.filter is BearerTokenAuthenticationFilter }

                Then("등록은 정확히 하나다") {
                    registrations shouldHaveSize 1
                }

                Then("서블릿 자동 등록이 꺼져 있어 보안 체인 밖에서는 실행되지 않는다") {
                    registrations
                        .single()
                        .isEnabled
                        .shouldBeFalse()
                }
            }
        }
    })
