package com.github.nexters.ppotto.auth.config

import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import jakarta.validation.Validation
import org.springframework.web.client.ResourceAccessException
import java.net.InetSocketAddress

class AuthHttpConfigTest :
    BehaviorSpec({
        Given("OAuth HTTP 제한시간이 0 이하일 때") {
            When("설정을 검증하면") {
                Then("연결 및 응답 제한시간 설정을 모두 거부한다") {
                    Validation.buildDefaultValidatorFactory().use { validatorFactory ->
                        val violations =
                            validatorFactory.validator
                                .validate(OAuthHttpProperties(0, -1))
                                .map { it.propertyPath.toString() }

                        violations shouldContainExactlyInAnyOrder
                            listOf("connectTimeoutMillis", "readTimeoutMillis")
                    }
                }
            }
        }

        Given("응답 제한시간보다 늦게 응답하는 OAuth 서버가 있을 때") {
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.createContext("/slow") { exchange ->
                Thread.sleep(500)
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            server.start()
            val client =
                AuthHttpConfig()
                    .restClientBuilder(OAuthHttpProperties(1_000, 50))
                    .build()

            When("OAuth 서버를 호출하면") {
                Then("응답 제한시간 초과 예외가 발생한다") {
                    try {
                        shouldThrow<ResourceAccessException> {
                            client
                                .get()
                                .uri("http://localhost:${server.address.port}/slow")
                                .retrieve()
                                .toBodilessEntity()
                        }
                    } finally {
                        server.stop(0)
                    }
                }
            }
        }
    })
