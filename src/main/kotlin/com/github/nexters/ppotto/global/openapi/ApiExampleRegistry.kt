package com.github.nexters.ppotto.global.openapi

import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaMethod

@Component
class ApiExampleRegistry(
    providers: List<ApiExampleProvider>,
) {
    private val examplesByMethod: Map<Method, OperationExamples> =
        providers
            .flatMap { provider -> provider.examples.entries }
            .associate { (function, examples) ->
                (function.javaMethod ?: error("예시를 붙일 수 없는 함수입니다: $function")) to examples
            }

    val size: Int get() = examplesByMethod.size

    fun find(handlerMethod: HandlerMethod): OperationExamples? = examplesByMethod[declaredMethodOf(handlerMethod)]

    private fun declaredMethodOf(handlerMethod: HandlerMethod): Method =
        ClassUtils.getInterfaceMethodIfPossible(handlerMethod.method, handlerMethod.beanType)
}
