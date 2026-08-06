package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.support.AuthTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@Import(AuthTestConfig::class)
class AuthControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
) : IntegrationTest({
        Given("user와 terms application port가 연결된 상태에서") {
            When("provider별 필수 값이 없는 로그인 요청을 보내면") {
                Then("인증 컨트롤러가 요청을 받아 400을 반환한다") {
                    mockMvc
                        .perform(
                            post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"provider":"KAKAO"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("카카오 로그인 요청에 name을 담아 보내면") {
                Then("서버가 닉네임을 직접 조회하므로 400을 반환한다") {
                    mockMvc
                        .perform(
                            post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"provider":"KAKAO","accessToken":"kakao-token","name":"뽀또"}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("애플 로그인 요청의 name이 공백이면") {
                Then("400을 반환한다") {
                    mockMvc
                        .perform(
                            post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"provider":"APPLE","identityToken":"identity-token",
                                    "authorizationCode":"authorization-code","rawNonce":"raw-nonce","name":" "}
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }
    })
