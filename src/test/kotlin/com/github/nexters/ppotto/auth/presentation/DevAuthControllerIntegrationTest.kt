package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.UserJourneyTestConfig
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
// InMemoryRefreshTokenStore 재사용 목적 (테스트 환경에는 Redis가 없음)
@Import(UserJourneyTestConfig::class)
@TestPropertySource(properties = ["auth.dev-login.enabled=true"])
class DevAuthControllerIntegrationTest(
    mockMvc: MockMvc,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("dev 로그인이 활성화되고 카카오 유저가 가입된 상태에서") {
            fun signUpKakaoUser() = userRepository.save(OAuthProvider.KAKAO, "kakao-dev-1", "dev@ppotto.co.kr", "뽀또")

            When("올바른 이메일과 고정 비밀번호로 로그인하면") {
                Then("실제 토큰 쌍을 발급한다") {
                    signUpKakaoUser()
                    mockMvc
                        .perform(
                            post("/dev/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"email":"dev@ppotto.co.kr","password":"password1!"}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
                        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
                }
            }

            When("비밀번호가 틀리면") {
                Then("401을 반환한다") {
                    signUpKakaoUser()
                    mockMvc
                        .perform(
                            post("/dev/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"email":"dev@ppotto.co.kr","password":"wrong"}"""),
                        ).andExpect(status().isUnauthorized)
                }
            }

            When("가입되지 않은 이메일이면") {
                Then("401을 반환한다") {
                    mockMvc
                        .perform(
                            post("/dev/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"email":"nobody@ppotto.co.kr","password":"password1!"}"""),
                        ).andExpect(status().isUnauthorized)
                }
            }
        }
    })
