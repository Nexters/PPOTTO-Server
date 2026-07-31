package com.github.nexters.ppotto.global.error

import com.github.nexters.ppotto.support.IntegrationTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest(
    mockMvc: MockMvc,
) : IntegrationTest({
        Given("지원하지 않는 Content-Type으로 요청할 때") {
            When("텍스트 본문으로 로그인을 호출하면") {
                Then("COMMON-007 오류 봉투와 415를 반환한다") {
                    mockMvc
                        .perform(
                            post("/auth/login")
                                .header("X-API-Version", "1")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("""{"provider":"KAKAO","accessToken":"token"}"""),
                        ).andExpect(status().isUnsupportedMediaType)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-007"))
                }
            }
        }

        Given("지원하지 않는 Accept 형식으로 요청할 때") {
            When("이미지 응답을 요구하며 약관을 조회하면") {
                Then("500으로 강등하지 않고 406을 반환한다") {
                    mockMvc
                        .perform(
                            get("/terms")
                                .header("X-API-Version", "1")
                                .accept(MediaType.IMAGE_PNG),
                        ).andExpect(status().isNotAcceptable)
                }
            }
        }
    })
