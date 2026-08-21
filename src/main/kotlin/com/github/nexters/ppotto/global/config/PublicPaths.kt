package com.github.nexters.ppotto.global.config

object PublicPaths {
    private const val HEALTH_PATH = "/actuator/health"
    private const val TERMS_PATH = "/terms"
    private const val STICKER_DETAIL_PATTERN = "/stickers/*"
    private val STICKER_DETAIL_REGEX = Regex("/stickers/[^/]+")
    private val PUBLIC_API_EXACT_PATHS = setOf("/auth/login", "/auth/refresh")
    private val DOCUMENT_PATH_PREFIXES = setOf("/swagger-ui", "/v3/api-docs")

    val PUBLIC_API_PATTERNS = (PUBLIC_API_EXACT_PATHS + "$HEALTH_PATH/**").toTypedArray()
    val DOCUMENT_PATTERNS = (DOCUMENT_PATH_PREFIXES.map { "$it/**" } + "/swagger-ui.html").toTypedArray()
    val OPTIONAL_AUTH_GET_PATTERNS = arrayOf(TERMS_PATH, STICKER_DETAIL_PATTERN)

    fun isPublicApi(path: String): Boolean =
        path in PUBLIC_API_EXACT_PATHS ||
            path == HEALTH_PATH ||
            path.startsWith("$HEALTH_PATH/")

    fun isDocument(path: String): Boolean = DOCUMENT_PATH_PREFIXES.any(path::startsWith)

    fun isOptionalAuthGet(path: String): Boolean = path == TERMS_PATH || STICKER_DETAIL_REGEX.matches(path)
}
