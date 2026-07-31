package com.github.nexters.ppotto.global.openapi

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod

@Component
class ApiExampleOperationCustomizer(
    private val registry: ApiExampleRegistry,
    private val exampleFactory: ApiExampleFactory,
) : OperationCustomizer {
    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation =
        operation.also { customized ->
            registry.find(handlerMethod)?.let { operationExamples ->
                customized.requestBody
                    ?.content
                    .inject(operationExamples.request)
                operationExamples.responses.forEach { (responseCode, examples) ->
                    customized.responses
                        ?.get(responseCode)
                        ?.content
                        .inject(examples)
                }
            }
        }

    private fun Content?.inject(examples: List<ApiExample>) {
        this?.values?.forEach { mediaType ->
            examples.forEach { mediaType.addExamples(it.name, exampleFactory.create(it)) }
        }
    }
}
