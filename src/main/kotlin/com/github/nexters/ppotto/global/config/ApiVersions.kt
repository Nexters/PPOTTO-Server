package com.github.nexters.ppotto.global.config

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping

object ApiVersions {
    const val API_VERSION_HEADER = "X-API-Version"
    const val DEFAULT_API_VERSION = "1"

    val SUPPORTED_API_VERSIONS = listOf("1", "2")

    fun declaredVersionOf(handlerType: Class<*>): String? =
        AnnotatedElementUtils
            .findMergedAnnotation(handlerType, RequestMapping::class.java)
            ?.version
            ?.takeIf { it.isNotBlank() }

    fun isVersionPinned(handlerType: Class<*>): Boolean = declaredVersionOf(handlerType)?.endsWith(BASELINE_MARKER) == false

    fun acceptedVersionsOf(handlerType: Class<*>): List<String> {
        val declared = declaredVersionOf(handlerType) ?: return SUPPORTED_API_VERSIONS
        val baseline = declared.removeSuffix(BASELINE_MARKER)
        if (!declared.endsWith(BASELINE_MARKER)) return listOf(baseline)
        return SUPPORTED_API_VERSIONS.filter { it >= baseline }
    }

    private const val BASELINE_MARKER = "+"
}
