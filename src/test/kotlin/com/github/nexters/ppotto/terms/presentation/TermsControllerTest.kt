package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.terms.support.saveTerm
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasItem
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class TermsControllerTest(
    mockMvc: MockMvc,
    termsService: TermsService,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("인증된 사용자와 현재 필수 약관이 있는 상태에서") {
            val userId = userRepository.saveTestUser().id
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.value, null, emptyList())
            val code = "API-${UUID.randomUUID()}"
            val term = dslContext.saveTerm(code, isRequired = true)

            When("현재 약관을 조회하면") {
                val result = mockMvc.perform(get("/terms").with(authentication(authentication)))

                Then("API 명세 필드를 미동의 상태로 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].code").value(hasItem(code)))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].version").value(hasItem("1.0")))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].isRequired").value(hasItem(true)))
                        .andExpect(
                            jsonPath("$.data[?(@.id == '${term.id}')].contentUrl")
                                .value(hasItem(term.contentUrl)),
                        ).andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(false)))
                }
            }

            When("같은 동의를 두 번 제출하면") {
                val requestBody =
                    termsService
                        .findCurrentTerms(userId)
                        .filter { it.isRequired }
                        .joinToString(
                            prefix = "{\"termIds\":[\"",
                            postfix = "\"]}",
                            separator = "\",\"",
                        ) { it.id.toString() }
                val results =
                    List(2) {
                        mockMvc.perform(
                            post("/terms/agreements")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody),
                        )
                    }

                Then("두 요청 모두 빈 성공 응답을 반환한다") {
                    results.forEach {
                        it
                            .andExpect(status().isOk)
                            .andExpect(jsonPath("$.success").value(true))
                            .andExpect(jsonPath("$.data").doesNotExist())
                    }
                }

                Then("동의 이력은 한 건만 저장된다") {
                    dslContext.fetchCount(
                        TERM_AGREEMENTS,
                        TERM_AGREEMENTS.USER_ID
                            .eq(userId)
                            .and(TERM_AGREEMENTS.TERM_ID.eq(term.id)),
                    ) shouldBe 1
                }

                Then("이후 조회에서 동의 상태로 바뀐다") {
                    mockMvc
                        .perform(get("/terms").with(authentication(authentication)))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(true)))
                }
            }
        }

        Given("인증된 사용자와 동의하지 않은 필수 약관이 있는 상태에서") {
            val userId = userRepository.saveTestUser().id
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.value, null, emptyList())
            dslContext.saveTerm("REQUIRED-API-${UUID.randomUUID()}", isRequired = true)
            val optionalTerm = dslContext.saveTerm("OPTIONAL-API-${UUID.randomUUID()}")

            When("선택 약관만 동의하면") {
                val result =
                    mockMvc.perform(
                        post("/terms/agreements")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"termIds":["${optionalTerm.id}"]}"""),
                    )

                Then("TERM-001 오류를 반환한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("TERM-001"))
                        .andExpect(jsonPath("$.error.message").value("필수 약관에 동의해야 합니다."))
                }
            }
        }

        Given("인증 principal이 없는 상태에서") {
            val code = "PUBLIC-API-${UUID.randomUUID()}"
            val term = dslContext.saveTerm(code, isRequired = true)

            When("약관을 조회하면") {
                val result = mockMvc.perform(get("/terms"))

                Then("동의하지 않은 공개 약관 목록을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].code").value(hasItem(code)))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(false)))
                }
            }

            When("약관 동의를 제출하면") {
                val result =
                    mockMvc.perform(
                        post("/terms/agreements")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"termIds":[]}"""),
                    )

                Then("COMMON-004 오류를 반환한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
