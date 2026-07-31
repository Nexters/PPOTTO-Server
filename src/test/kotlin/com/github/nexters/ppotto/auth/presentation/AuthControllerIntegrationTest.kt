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
        }
    })
