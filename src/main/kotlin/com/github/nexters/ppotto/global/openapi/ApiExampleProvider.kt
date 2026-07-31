package com.github.nexters.ppotto.global.openapi

import kotlin.reflect.KFunction

interface ApiExampleProvider {
    val examples: Map<KFunction<*>, OperationExamples>
}
