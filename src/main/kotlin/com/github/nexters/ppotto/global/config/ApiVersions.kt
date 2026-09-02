package com.github.nexters.ppotto.global.config

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping

const val DEFAULT_API_VERSION = "1"

private const val BASELINE_MARKER = "+"

val SUPPORTED_API_VERSIONS = listOf("1", "2")

fun declaredVersionOf(handlerType: Class<*>): String? =
    AnnotatedElementUtils
        .findMergedAnnotation(handlerType, RequestMapping::class.java)
        ?.version
        ?.takeIf { it.isNotBlank() }

fun isVersionPinned(handlerType: Class<*>): Boolean = declaredVersionOf(handlerType)?.endsWith(BASELINE_MARKER) == false

fun acceptedVersionsOf(handlerType: Class<*>): List<String> =
    when (val declared = declaredVersionOf(handlerType)) {
        null -> SUPPORTED_API_VERSIONS
        else ->
            declared
                .removeSuffix(BASELINE_MARKER)
                .let { baseline ->
                    when {
                        declared.endsWith(BASELINE_MARKER) -> SUPPORTED_API_VERSIONS.filter { it >= baseline }
                        else -> listOf(baseline)
                    }
                }
    }
