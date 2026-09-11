package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.PpottoApplication
import com.github.nexters.ppotto.global.web.ApiVersions
import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

private val APPLICATION_PACKAGE: String = PpottoApplication::class.java.packageName

private val FROZEN_V1_HANDLERS =
    listOf(
        "BoardDetailController#get",
        "BoardLayoutController#update",
    )

private const val BASELINE_ENDPOINT = "/terms"

@AutoConfigureMockMvc
class ApiVersioningTest(
    @Qualifier("requestMappingHandlerMapping")
    handlerMapping: RequestMappingHandlerMapping,
    mockMvc: MockMvc,
) : IntegrationTest({
        Given("애플리케이션이 노출하는 매핑이 주어졌을 때") {
            val endpoints =
                handlerMapping.handlerMethods
                    .filterValues {
                        it.beanType.packageName
                            .startsWith(APPLICATION_PACKAGE)
                    }.entries
                    .groupBy({ (info, _) -> info.patternValues to info.methodsCondition.methods })

            When("검사 대상 엔드포인트를 모으면") {
                Then("하나 이상 찾는다") {
                    endpoints.size shouldBeGreaterThan 0
                }
            }

            ApiVersions.SUPPORTED_API_VERSIONS.forEach { version ->
                When("클라이언트가 X-API-Version $version 로 모든 엔드포인트를 호출하면") {
                    val unreachable =
                        endpoints
                            .filterValues { mappings ->
                                mappings.none { (_, handler) -> version in ApiVersions.acceptedVersionsOf(handler.beanType) }
                            }.map { (endpoint, _) -> endpoint.toString() }

                    Then("400으로 떨어지는 엔드포인트가 없다") {
                        unreachable.shouldBeEmpty()
                    }
                }
            }

            When("버전을 고정한 매핑을 모으면") {
                val pinned =
                    handlerMapping.handlerMethods
                        .filterValues {
                            it.beanType.packageName
                                .startsWith(APPLICATION_PACKAGE)
                        }.filterValues { ApiVersions.isVersionPinned(it.beanType) }
                        .map { (_, handler) ->
                            "${handler.beanType.simpleName}#${handler.method.name.substringBefore('-')}"
                        }

                Then("v2 대체본을 둔 보드 엔드포인트만 v1에 고정되어 있다") {
                    pinned shouldContainExactlyInAnyOrder FROZEN_V1_HANDLERS
                }
            }
        }

        Given("버전을 바꾸지 않은 baseline 엔드포인트 $BASELINE_ENDPOINT 가 있을 때") {
            When("X-API-Version 헤더 없이 호출하면") {
                val result = mockMvc.perform(get(BASELINE_ENDPOINT))

                Then("서버 기본 버전 ${ApiVersions.DEFAULT_API_VERSION} 로 처리해 200을 준다") {
                    result.andExpect(status().isOk)
                }
            }

            When("X-API-Version 2 로 호출하면") {
                val result = mockMvc.perform(get(BASELINE_ENDPOINT).header(ApiVersions.API_VERSION_HEADER, "2"))

                Then("baseline 매핑이 상위 버전도 받아 200을 준다") {
                    result.andExpect(status().isOk)
                }
            }

            When("지원하지 않는 X-API-Version 3 으로 호출하면") {
                val result = mockMvc.perform(get(BASELINE_ENDPOINT).header(ApiVersions.API_VERSION_HEADER, "3"))

                Then("400으로 거절한다") {
                    result.andExpect(status().isBadRequest)
                }
            }
        }
    })
