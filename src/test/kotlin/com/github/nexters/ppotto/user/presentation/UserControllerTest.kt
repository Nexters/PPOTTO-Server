package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.application.SocialUserCommand
import com.github.nexters.ppotto.user.application.UserService
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.github.nexters.ppotto.user.support.FakeCurrentUserProvider
import com.github.nexters.ppotto.user.support.UserTestConfig
import io.kotest.matchers.nulls.shouldBeNull
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
@Import(UserTestConfig::class)
class UserControllerTest(
    mockMvc: MockMvc,
    userService: UserService,
    userRepository: UserRepository,
    currentUserProvider: FakeCurrentUserProvider,
) : IntegrationTest({
        Given("인증된 사용자가 있을 때") {
            val registered =
                userService.findOrCreate(
                    SocialUserCommand(
                        provider = OAuthProvider.KAKAO,
                        providerUserId = "controller-${UUID.randomUUID()}",
                        email = "me@example.com",
                        providerRefreshToken = null,
                    ),
                )
            currentUserProvider.userId = registered.user.id

            When("내 정보를 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/users/me")
                            .header("X-API-Version", "1"),
                    )

                Then("공개 가능한 계정 정보만 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(
                            jsonPath("$.data.id").value(
                                registered.user.id
                                    .toString(),
                            ),
                        ).andExpect(jsonPath("$.data.provider").value("KAKAO"))
                        .andExpect(jsonPath("$.data.email").value("me@example.com"))
                        .andExpect(jsonPath("$.data.createdAt").exists())
                        .andExpect(jsonPath("$.data.providerUserId").doesNotExist())
                        .andExpect(jsonPath("$.data.providerRefreshToken").doesNotExist())
                }
            }

            When("회원 탈퇴를 요청하면") {
                val result =
                    mockMvc.perform(
                        delete("/users/me")
                            .header("X-API-Version", "1"),
                    )

                Then("탈퇴 처리 후 빈 성공 응답을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                    userRepository.findById(registered.user.id).shouldBeNull()
                }
            }
        }

        Given("인증되지 않은 요청일 때") {
            currentUserProvider.userId = null

            When("내 정보를 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/users/me")
                            .header("X-API-Version", "1"),
                    )

                Then("인증 오류를 응답한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
