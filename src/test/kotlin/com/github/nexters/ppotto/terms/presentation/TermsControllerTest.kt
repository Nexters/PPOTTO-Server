package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.enums.OauthProvider
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.terms.support.TermsTestSecurityConfig
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasItem
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
@Import(TermsTestSecurityConfig::class)
class TermsControllerTest(
    @Autowired val mockMvc: MockMvc,
    termsService: TermsService,
    dslContext: DSLContext,
) : IntegrationTest({
        fun saveUser(): UUID {
            val suffix = UUID.randomUUID()
            return dslContext
                .insertInto(
                    USERS,
                    USERS.PROVIDER,
                    USERS.PROVIDER_USER_ID,
                    USERS.EMAIL,
                    USERS.NAME,
                ).values(
                    OauthProvider.KAKAO,
                    "terms-controller-$suffix",
                    "terms-controller-$suffix@example.com",
                    "약관컨트롤러사용자",
                ).returning(USERS.ID)
                .fetchOne(USERS.ID)!!
                .value
        }

        fun saveTerm(
            code: String,
            isRequired: Boolean,
        ) = dslContext
            .insertInto(
                TERMS,
                TERMS.CODE,
                TERMS.VERSION,
                TERMS.IS_REQUIRED,
                TERMS.CONTENT_URL,
                TERMS.EFFECTIVE_AT,
            ).values(code, "1.0", isRequired, "https://example.com/$code", Instant.now().minusSeconds(60))
            .returning()
            .fetchOne()!!

        Given("인증된 사용자와 현재 필수 약관이 있는 상태에서") {
            val userId = saveUser()
            val code = "API-${UUID.randomUUID()}"
            val term = saveTerm(code, true)

            When("현재 약관을 조회하고 같은 동의를 두 번 제출하면") {
                mockMvc
                    .perform(
                        get("/terms")
                            .requestAttr(TermsTestSecurityConfig.USER_ID_ATTRIBUTE, userId),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].code").value(hasItem(code)))
                    .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].version").value(hasItem("1.0")))
                    .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].isRequired").value(hasItem(true)))
                    .andExpect(
                        jsonPath("$.data[?(@.id == '${term.id}')].contentUrl")
                            .value(hasItem("https://example.com/$code")),
                    ).andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(false)))

                val requiredTermIds =
                    termsService
                        .findCurrentTerms(UserId(userId))
                        .filter { it.isRequired }
                        .map { it.id }
                val requestBody =
                    requiredTermIds.joinToString(
                        prefix = "{\"termIds\":[\"",
                        postfix = "\"]}",
                        separator = "\",\"",
                    )
                repeat(2) {
                    mockMvc
                        .perform(
                            post("/terms/agreements")
                                .requestAttr(TermsTestSecurityConfig.USER_ID_ATTRIBUTE, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("API 명세 필드가 반환되고 동의 이력은 한 건만 저장된다") {
                    dslContext.fetchCount(
                        TERM_AGREEMENTS,
                        TERM_AGREEMENTS.USER_ID
                            .eq(UserId(userId))
                            .and(TERM_AGREEMENTS.TERM_ID.eq(term.id)),
                    ) shouldBe 1

                    mockMvc
                        .perform(
                            get("/terms")
                                .requestAttr(TermsTestSecurityConfig.USER_ID_ATTRIBUTE, userId),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(true)))
                }
            }
        }

        Given("인증된 사용자와 동의하지 않은 필수 약관이 있는 상태에서") {
            val userId = saveUser()
            saveTerm("REQUIRED-API-${UUID.randomUUID()}", true)
            val optionalTerm = saveTerm("OPTIONAL-API-${UUID.randomUUID()}", false)

            When("선택 약관만 동의하면") {
                Then("TERM-001 오류를 반환한다") {
                    mockMvc
                        .perform(
                            post("/terms/agreements")
                                .requestAttr(TermsTestSecurityConfig.USER_ID_ATTRIBUTE, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"termIds":["${optionalTerm.id}"]}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("TERM-001"))
                        .andExpect(jsonPath("$.error.message").value("필수 약관에 동의해야 합니다."))
                }
            }
        }

        Given("인증 principal이 없는 상태에서") {
            When("약관을 조회하면") {
                val code = "PUBLIC-API-${UUID.randomUUID()}"
                val term = saveTerm(code, true)

                Then("동의하지 않은 공개 약관 목록을 반환한다") {
                    mockMvc
                        .perform(get("/terms"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].code").value(hasItem(code)))
                        .andExpect(jsonPath("$.data[?(@.id == '${term.id}')].agreed").value(hasItem(false)))
                }
            }

            When("약관 동의를 제출하면") {
                Then("COMMON-004 오류를 반환한다") {
                    mockMvc
                        .perform(
                            post("/terms/agreements")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"termIds":[]}"""),
                        ).andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
