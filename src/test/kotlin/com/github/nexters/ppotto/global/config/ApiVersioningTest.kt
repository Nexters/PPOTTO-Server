package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

private const val APPLICATION_PACKAGE = "com.github.nexters.ppotto"

private val FROZEN_V1_HANDLERS =
    listOf(
        "BoardDetailController#get",
        "BoardLayoutController#update",
    )

class ApiVersioningTest(
    @Qualifier("requestMappingHandlerMapping")
    handlerMapping: RequestMappingHandlerMapping,
) : IntegrationTest({
        Given("애플리케이션이 노출하는 매핑이 주어졌을 때") {
            val endpoints =
                handlerMapping.handlerMethods
                    .filterValues {
                        it.beanType.packageName
                            .startsWith(APPLICATION_PACKAGE)
                    }.entries
                    .groupBy({ (info, _) -> info.patternValues to info.methodsCondition.methods })

            Then("검사 대상 엔드포인트를 하나 이상 찾는다") {
                endpoints.size shouldBeGreaterThan 0
            }

            SUPPORTED_API_VERSIONS.forEach { version ->
                When("클라이언트가 X-API-Version $version 로 모든 엔드포인트를 호출하면") {
                    val unreachable =
                        endpoints
                            .filterValues { mappings ->
                                mappings.none { (_, handler) -> version in acceptedVersionsOf(handler.beanType) }
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
                        }.filterValues { isVersionPinned(it.beanType) }
                        .map { (_, handler) ->
                            "${handler.beanType.simpleName}#${handler.method.name.substringBefore('-')}"
                        }

                Then("v2 대체본을 둔 보드 엔드포인트만 v1에 고정되어 있다") {
                    pinned shouldContainExactlyInAnyOrder FROZEN_V1_HANDLERS
                }
            }
        }
    })
