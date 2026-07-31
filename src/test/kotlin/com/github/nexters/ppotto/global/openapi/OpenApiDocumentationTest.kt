package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.support.IntegrationTest
import org.hamcrest.Matchers.hasItem
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class OpenApiDocumentationTest(
    mockMvc: MockMvc,
) : IntegrationTest({
        Given("OpenAPI 문서를 조회하면") {
            val result = mockMvc.perform(get("/v3/api-docs"))

            Then("서비스 정보와 Bearer 인증 스키마를 제공한다") {
                result
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.info.title").value("뽀또 API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            }

            Then("엔드포인트 설명과 API 버전 헤더를 제공한다") {
                result
                    .andExpect(jsonPath("$['paths']['/analysis']['post']['summary']").value("분석 생성"))
                    .andExpect(jsonPath("$['paths']['/analysis']['post']['tags']").value(hasItem("분석")))
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['parameters'][*].name")
                            .value(hasItem("X-API-Version")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['CreateAnalysisRequest']['description']")
                            .value("분석 생성과 사진 업로드 URL 발급 요청"),
                    )
            }

            Then("필수 인증과 선택 인증을 구분한다") {
                result
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['security'][0]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/terms']['get']['security'][1]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['401']['description']")
                            .value("access token이 없거나 유효하지 않음"),
                    ).andExpect(
                        jsonPath("$['paths']['/terms']['get']['responses']['401']['description']")
                            .value("전달한 access token이 유효하지 않음"),
                    )
            }

            Then("공통 오류 응답 설명을 제공한다") {
                result
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['400']['description']")
                            .value("요청 값이 올바르지 않음"),
                    ).andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['404']['description']")
                            .value("요청한 리소스를 찾을 수 없음"),
                    ).andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['409']['description']")
                            .value("현재 상태와 요청이 충돌함"),
                    )
            }
        }
    })
