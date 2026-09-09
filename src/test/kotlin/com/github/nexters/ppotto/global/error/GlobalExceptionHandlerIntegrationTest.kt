package com.github.nexters.ppotto.global.error

import com.github.nexters.ppotto.support.IntegrationTest
import org.hamcrest.Matchers.hasItem
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest(
    mockMvc: MockMvc,
    jsonMapper: JsonMapper,
) : IntegrationTest({
        val advisedMockMvc =
            MockMvcBuilders
                .standaloneSetup(ThrowingTestController())
                .setControllerAdvice(GlobalExceptionHandler())
                .setMessageConverters(JacksonJsonHttpMessageConverter(jsonMapper))
                .build()

        Given("지원하지 않는 Content-Type으로 요청할 때") {
            When("텍스트 본문으로 로그인을 호출하면") {
                val result =
                    mockMvc.perform(
                        post("/auth/login")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("""{"provider":"KAKAO","accessToken":"token"}"""),
                    )

                Then("COMMON-007 오류 봉투와 415를 반환한다") {
                    result
                        .andExpect(status().isUnsupportedMediaType)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-007"))
                }
            }
        }

        Given("지원하지 않는 Accept 형식으로 요청할 때") {
            When("이미지 응답을 요구하며 약관을 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/terms")
                            .header("X-API-Version", "1")
                            .accept(MediaType.IMAGE_PNG),
                    )

                Then("500으로 강등하지 않고 406을 반환한다") {
                    result.andExpect(status().isNotAcceptable)
                }
            }
        }

        Given("매핑에 없는 HTTP 메서드로 요청할 때") {
            When("GET만 있는 약관 경로를 PUT으로 호출하면") {
                val result =
                    mockMvc.perform(
                        put("/terms")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"),
                    )

                Then("COMMON-003 오류 봉투와 405를 반환한다") {
                    result
                        .andExpect(status().isMethodNotAllowed)
                        .andExpect(jsonPath("$.error.code").value("COMMON-003"))
                }
            }
        }

        Given("매핑되지 않은 경로로 요청할 때") {
            When("존재하지 않는 리소스를 조회하면") {
                val result = mockMvc.perform(get("/definitely-not-a-mapped-path").header("X-API-Version", "1"))

                Then("COMMON-002 오류 봉투와 404를 반환한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("COMMON-002"))
                }
            }
        }

        Given("요청 바디 검증에 실패할 때") {
            When("provider 없이 로그인을 호출하면") {
                val result =
                    mockMvc.perform(
                        post("/auth/login")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"provider":null,"accessToken":"token"}"""),
                    )

                Then("COMMON-001 오류 봉투와 400을 반환한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("fieldErrors에 실패한 필드와 사유를 담는다") {
                    result
                        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("provider")))
                        .andExpect(jsonPath("$.error.fieldErrors[0].reason").isNotEmpty)
                }
            }
        }

        Given("컨트롤러가 처리하지 못한 예외를 던질 때") {
            When("일반 예외가 핸들러 밖으로 나오면") {
                val result = advisedMockMvc.perform(get("/test-only/unhandled"))

                Then("COMMON-000 오류 봉투와 500을 반환한다") {
                    result
                        .andExpect(status().isInternalServerError)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-000"))
                }
            }
        }

        Given("컨트롤러가 상태 코드를 지정한 프레임워크 예외를 던질 때") {
            When("403 ResponseStatusException이 나오면") {
                val result = advisedMockMvc.perform(get("/test-only/forbidden"))

                Then("500으로 강등하지 않고 403과 COMMON-005를 유지한다") {
                    result
                        .andExpect(status().isForbidden)
                        .andExpect(jsonPath("$.error.code").value("COMMON-005"))
                }
            }

            When("409 ResponseStatusException이 나오면") {
                val result = advisedMockMvc.perform(get("/test-only/conflict"))

                Then("409와 COMMON-006을 유지한다") {
                    result
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("COMMON-006"))
                }
            }

            When("공통 코드에 없는 4xx 상태가 나오면") {
                val result = advisedMockMvc.perform(get("/test-only/teapot"))

                Then("상태 코드는 그대로 두고 COMMON-001 봉투로 감싼다") {
                    result
                        .andExpect(status().isIAmATeapot)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("협상 가능한 응답 형식이 없을 때") {
            When("Accept를 만족하지 못해 406이 나오면") {
                val result = advisedMockMvc.perform(get("/test-only/not-acceptable").accept(MediaType.APPLICATION_JSON))

                Then("COMMON-008 오류 봉투와 406을 반환한다") {
                    result
                        .andExpect(status().isNotAcceptable)
                        .andExpect(jsonPath("$.error.code").value("COMMON-008"))
                        .andExpect(jsonPath("$.error.message").value("지원하지 않는 Accept 형식입니다."))
                }
            }
        }
    })

// @RestController 는 테스트 소스에 있어도 @SpringBootTest 컴포넌트 스캔에 잡힌다.
// 활성화하지 않는 프로파일을 걸어 실제 컨텍스트에서는 빈이 되지 않게 하고, standaloneSetup 에만 넘긴다.
@RestController
@Profile("standalone-only")
class ThrowingTestController {
    @GetMapping("/test-only/unhandled")
    fun unhandled(): Nothing = throw IllegalStateException("의도적으로 처리하지 않은 예외")

    @GetMapping("/test-only/forbidden")
    fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN)

    @GetMapping("/test-only/conflict")
    fun conflict(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT)

    @GetMapping("/test-only/teapot")
    fun teapot(): Nothing = throw ResponseStatusException(HttpStatus.I_AM_A_TEAPOT)

    @GetMapping("/test-only/not-acceptable")
    fun notAcceptable(): Nothing = throw HttpMediaTypeNotAcceptableException(listOf(MediaType.APPLICATION_JSON))
}
