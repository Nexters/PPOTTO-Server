package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.support.AuthTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.startsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

private const val REFRESH_TOKEN_LENGTH = 43

@AutoConfigureMockMvc
@Import(AuthTestConfig::class)
class AuthControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
) : IntegrationTest({
        fun postJson(
            path: String,
            body: String,
        ) = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

        fun login(providerUserId: String): String =
            postJson("/auth/login", """{"provider":"KAKAO","accessToken":"$providerUserId"}""")
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        Given("provider만 담고 provider별 필수 값을 빠뜨린 로그인 요청이 있을 때") {
            When("앱 로그인을 요청하면") {
                val response = postJson("/auth/login", """{"provider":"KAKAO"}""")

                Then("400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("애플 로그인 요청이 rawNonce를 빠뜨렸을 때") {
            When("앱 로그인을 요청하면") {
                val response =
                    postJson(
                        "/auth/login",
                        """{"provider":"APPLE","identityToken":"identity-token","authorizationCode":"code"}""",
                    )

                Then("400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("카카오 로그인 요청이 name을 담고 있을 때") {
            When("앱 로그인을 요청하면") {
                val response =
                    postJson("/auth/login", """{"provider":"KAKAO","accessToken":"kakao-token","name":"뽀또"}""")

                Then("서버가 닉네임을 직접 조회하므로 400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("웹 로그인 요청이 아직 지원하지 않는 APPLE provider일 때") {
            When("웹 로그인을 요청하면") {
                val response =
                    postJson(
                        "/auth/login/web",
                        """{"provider":"APPLE","authorizationCode":"code","redirectUri":"https://ppotto.co.kr/oauth/apple"}""",
                    )

                Then("400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("웹 로그인 요청에 redirectUri가 없을 때") {
            When("웹 로그인을 요청하면") {
                val response = postJson("/auth/login/web", """{"provider":"KAKAO","authorizationCode":"code"}""")

                Then("400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("애플 로그인 요청의 name이 공백일 때") {
            When("앱 로그인을 요청하면") {
                val response =
                    postJson(
                        "/auth/login",
                        """
                        {"provider":"APPLE","identityToken":"identity-token",
                        "authorizationCode":"authorization-code","rawNonce":"raw-nonce","name":" "}
                        """.trimIndent(),
                    )

                Then("400 COMMON-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("가입한 적 없는 카카오 계정이 앱 로그인을 시도할 때") {
            val providerUserId = "controller-${UUID.randomUUID()}"

            When("앱 로그인을 요청하면") {
                val response = postJson("/auth/login", """{"provider":"KAKAO","accessToken":"$providerUserId"}""")

                Then("성공 봉투에 토큰 쌍과 만료 초와 신규 가입 여부를 담아 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.error").doesNotExist())
                        .andExpect(jsonPath("$.data.accessToken").value(startsWith("eyJ")))
                        .andExpect(jsonPath("$.data.refreshToken").isString)
                        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                        .andExpect(jsonPath("$.data.isNewUser").value(true))
                        .andExpect(jsonPath("$.data.pendingTerms", hasSize<Any>(0)))
                }

                Then("refresh token은 JWT가 아닌 32바이트 랜덤 문자열이다") {
                    val body =
                        response
                            .andReturn()
                            .response
                            .getContentAsString(Charsets.UTF_8)
                    JsonPath.read<String>(body, "$.data.refreshToken").length shouldBe REFRESH_TOKEN_LENGTH
                }
            }
        }

        Given("브라우저가 카카오 인가 코드를 받아 왔을 때") {
            val authorizationCode = "web-controller-${UUID.randomUUID()}"

            When("웹 로그인을 요청하면") {
                val response =
                    postJson(
                        "/auth/login/web",
                        """
                        {"provider":"KAKAO","authorizationCode":"$authorizationCode",
                        "redirectUri":"https://ppotto.co.kr/oauth/kakao"}
                        """.trimIndent(),
                    )

                Then("앱 로그인과 같은 성공 봉투를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.accessToken").value(startsWith("eyJ")))
                        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                        .andExpect(jsonPath("$.data.isNewUser").value(true))
                        .andExpect(jsonPath("$.data.pendingTerms", hasSize<Any>(0)))
                }
            }
        }

        Given("로그인으로 refresh token을 발급받았을 때") {
            val body = login("refresh-controller-${UUID.randomUUID()}")
            val refreshToken = JsonPath.read<String>(body, "$.data.refreshToken")

            When("토큰 재발급을 요청하면") {
                val response = postJson("/auth/refresh", """{"refreshToken":"$refreshToken"}""")

                Then("새 토큰 쌍과 만료 초를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.accessToken").value(startsWith("eyJ")))
                        .andExpect(jsonPath("$.data.refreshToken").isString)
                        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                }
            }
        }

        Given("로그인한 사용자가 access token을 들고 있을 때") {
            val body = login("logout-controller-${UUID.randomUUID()}")
            val accessToken = JsonPath.read<String>(body, "$.data.accessToken")
            val refreshToken = JsonPath.read<String>(body, "$.data.refreshToken")

            When("로그아웃을 요청하면") {
                val response =
                    mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $accessToken"))

                Then("데이터 없는 성공 봉투를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("폐기된 refresh token으로는 401 AUTH-002를 반환한다") {
                    postJson("/auth/refresh", """{"refreshToken":"$refreshToken"}""")
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("AUTH-002"))
                }
            }
        }
    })
