package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private const val APPLICATION_PACKAGE = "com.github.nexters.ppotto"

private fun JsonNode?.entries(): List<Pair<String, JsonNode>> =
    this
        ?.properties()
        ?.map { it.key to it.value }
        .orEmpty()

private fun JsonNode.operations(): List<Pair<String, JsonNode>> =
    this["paths"].entries().flatMap { (path, methods) ->
        methods.entries().map { (method, operation) -> "$method $path" to operation }
    }

private fun JsonNode.exampleHolders(): List<Pair<String, JsonNode>> =
    operations().flatMap { (operationName, operation) ->
        operation["responses"].entries().map { (responseCode, response) -> "$operationName $responseCode" to response } +
            listOfNotNull(operation["requestBody"]?.let { "$operationName requestBody" to it })
    }

private fun HandlerMethod.label(): String = "${beanType.simpleName}#${method.name}"

@AutoConfigureMockMvc
class OpenApiExampleWiringTest(
    mockMvc: MockMvc,
    objectMapper: ObjectMapper,
    registry: ApiExampleRegistry,
    @Qualifier("requestMappingHandlerMapping")
    handlerMapping: RequestMappingHandlerMapping,
) : IntegrationTest({
        Given("애플리케이션이 노출하는 핸들러 메서드가 주어졌을 때") {
            val handlerMethods: List<HandlerMethod> =
                handlerMapping.handlerMethods.values
                    .filter {
                        it.beanType.packageName
                            .startsWith(APPLICATION_PACKAGE)
                    }.distinctBy { it.method }

            Then("검사 대상 핸들러 메서드를 하나 이상 찾는다") {
                handlerMethods.size shouldBeGreaterThan 0
            }

            Then("모든 핸들러 메서드에 예시가 배선되어 있다") {
                handlerMethods
                    .filter { registry.find(it) == null }
                    .map(HandlerMethod::label)
                    .shouldBeEmpty()
            }

            Then("배선된 예시에 대응하는 핸들러 메서드가 없는 항목이 없다") {
                handlerMethods.count { registry.find(it) != null } shouldBe registry.size
            }

            Then("모든 핸들러 메서드가 응답 예시를 하나 이상 가진다") {
                handlerMethods
                    .filter {
                        registry
                            .find(it)
                            ?.responses
                            .orEmpty()
                            .isEmpty()
                    }.map(HandlerMethod::label)
                    .shouldBeEmpty()
            }
        }

        Given("OpenAPI 문서를 조회하면") {
            val document =
                mockMvc
                    .perform(get("/v3/api-docs"))
                    .andReturn()
                    .response
                    .contentAsString
                    .let(objectMapper::readTree)

            Then("본문이 있는 모든 응답에 예시가 하나 이상 있다") {
                document
                    .operations()
                    .flatMap { (operationName, operation) ->
                        operation["responses"].entries().flatMap { (responseCode, response) ->
                            response["content"]
                                .entries()
                                .filter { (_, mediaType) -> mediaType["examples"].entries().isEmpty() }
                                .map { (name, _) -> "$operationName $responseCode $name" }
                        }
                    }.shouldBeEmpty()
            }

            Then("모든 예시가 값을 담고 있다") {
                document
                    .exampleHolders()
                    .flatMap { (holderName, holder) ->
                        holder["content"].entries().flatMap { (_, mediaType) ->
                            mediaType["examples"].entries().map { (name, example) -> "$holderName $name" to example }
                        }
                    }.filter { (_, example) -> example["value"] == null || example["value"].isNull }
                    .map { (name, _) -> name }
                    .shouldBeEmpty()
            }
        }
    })
