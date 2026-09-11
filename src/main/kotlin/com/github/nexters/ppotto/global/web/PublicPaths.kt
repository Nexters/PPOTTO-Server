package com.github.nexters.ppotto.global.web

object PublicPaths {
    private const val ACTUATOR_PATH_PREFIX = "/actuator"
    private const val HEALTH_PATH = "$ACTUATOR_PATH_PREFIX/health"
    private const val TERMS_PATH = "/terms"
    private const val SHARED_RECAP_PATTERN = "/stickers/shared/*"
    private val PUBLIC_API_EXACT_PATHS = setOf("/auth/login", "/auth/login/web", "/auth/refresh")
    private val DOCUMENT_PATH_PREFIXES = setOf("/swagger-ui", "/v3/api-docs")

    val PUBLIC_API_PATTERNS = (PUBLIC_API_EXACT_PATHS + "$HEALTH_PATH/**").toTypedArray()
    val DOCUMENT_PATTERNS = (DOCUMENT_PATH_PREFIXES.map { "$it/**" } + "/swagger-ui.html").toTypedArray()
    val OPTIONAL_AUTH_GET_PATTERNS = arrayOf(TERMS_PATH, SHARED_RECAP_PATTERN)

    fun isPublicApi(path: String): Boolean =
        path in PUBLIC_API_EXACT_PATHS ||
            path == HEALTH_PATH ||
            path.startsWith("$HEALTH_PATH/")

    fun isDocument(path: String): Boolean = DOCUMENT_PATH_PREFIXES.any(path::startsWith)

    fun isActuator(path: String): Boolean = path.startsWith(ACTUATOR_PATH_PREFIX)
}
