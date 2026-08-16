package com.github.nexters.ppotto.global.observability

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.charset.Charset

object HttpPayloadAttributes {
    const val FILTERED = "[Filtered]"
    const val MAX_BODY_CHARS = 16384
    const val REQUEST_CACHE_LIMIT_BYTES = 65536

    fun requestHeaders(headers: Map<String, List<String>>): Map<String, String> =
        headers.entries.associate { (name, values) ->
            "$REQUEST_HEADER_PREFIX${name.lowercase()}" to maskedHeaderValue(name, values)
        }

    fun responseHeaders(headers: Map<String, List<String>>): Map<String, String> =
        headers.entries.associate { (name, values) ->
            "$RESPONSE_HEADER_PREFIX${name.lowercase()}" to maskedHeaderValue(name, values)
        }

    fun requestBody(
        body: ByteArray,
        contentType: String?,
        characterEncoding: String?,
    ): Map<String, Any> = bodyAttributes(REQUEST_BODY_KEY, REQUEST_BODY_SIZE_KEY, body, contentType, characterEncoding)

    fun responseBody(
        body: ByteArray,
        contentType: String?,
        characterEncoding: String?,
    ): Map<String, Any> = bodyAttributes(RESPONSE_BODY_KEY, RESPONSE_BODY_SIZE_KEY, body, contentType, characterEncoding)
}

private fun bodyAttributes(
    bodyKey: String,
    sizeKey: String,
    body: ByteArray,
    contentType: String?,
    characterEncoding: String?,
): Map<String, Any> =
    when {
        body.isEmpty() -> emptyMap()
        !isJson(contentType) -> mapOf(sizeKey to body.size)
        else ->
            mapOf(
                sizeKey to body.size,
                bodyKey to redactedBody(body.toString(charsetOf(characterEncoding))),
            )
    }

private fun redactedBody(raw: String): String =
    runCatching { jsonMapper.readTree(raw) }
        .map { node -> jsonMapper.writeValueAsString(redactNode(node)) }
        .getOrElse { raw }
        .let(::truncated)

private fun redactNode(node: JsonNode): JsonNode =
    when {
        node.isObject -> redactObject(node as ObjectNode)
        node.isArray -> jsonMapper.createArrayNode().apply { node.forEach { add(redactNode(it)) } }
        else -> node
    }

private fun redactObject(node: ObjectNode): ObjectNode =
    jsonMapper.createObjectNode().apply {
        node.properties().forEach { (name, value) ->
            when {
                isSensitiveKey(name) && isRedactable(value) -> put(name, HttpPayloadAttributes.FILTERED)
                else -> set(name, redactNode(value))
            }
        }
    }

private fun isRedactable(value: JsonNode): Boolean = !value.isNumber && !value.isBoolean && !value.isNull

private fun maskedHeaderValue(
    name: String,
    values: List<String>,
): String =
    when {
        isSensitiveHeader(name) -> HttpPayloadAttributes.FILTERED
        else -> truncated(values.joinToString(HEADER_VALUE_SEPARATOR))
    }

private fun isSensitiveHeader(name: String): Boolean =
    name.lowercase().let { lowered ->
        lowered in SENSITIVE_HEADERS || SENSITIVE_KEY_FRAGMENTS.any(lowered::contains)
    }

private fun isSensitiveKey(name: String): Boolean =
    name.lowercase().let { lowered ->
        SENSITIVE_KEY_FRAGMENTS.any(lowered::contains)
    }

private fun isJson(contentType: String?): Boolean =
    contentType
        ?.substringBefore(CONTENT_TYPE_PARAMETER_SEPARATOR)
        ?.trim()
        ?.lowercase()
        ?.let { it == APPLICATION_JSON || it.endsWith(JSON_SUFFIX) }
        ?: false

private fun charsetOf(characterEncoding: String?): Charset =
    characterEncoding
        ?.let { encoding -> runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8) }
        ?: Charsets.UTF_8

private fun truncated(value: String): String =
    when {
        value.length <= HttpPayloadAttributes.MAX_BODY_CHARS -> value
        else -> value.take(HttpPayloadAttributes.MAX_BODY_CHARS) + TRUNCATION_MARKER
    }

private val jsonMapper = JsonMapper.builder().build()

private const val REQUEST_HEADER_PREFIX = "http.request.header."
private const val RESPONSE_HEADER_PREFIX = "http.response.header."
private const val REQUEST_BODY_KEY = "http.request.body.data"
private const val RESPONSE_BODY_KEY = "http.response.body.data"
private const val REQUEST_BODY_SIZE_KEY = "http.request.body.size"
private const val RESPONSE_BODY_SIZE_KEY = "http.response.body.size"
private const val TRUNCATION_MARKER = "…[truncated]"
private const val HEADER_VALUE_SEPARATOR = ", "
private const val CONTENT_TYPE_PARAMETER_SEPARATOR = ';'
private const val APPLICATION_JSON = "application/json"
private const val JSON_SUFFIX = "+json"

private val SENSITIVE_HEADERS =
    setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
    )

private val SENSITIVE_KEY_FRAGMENTS =
    listOf(
        "authorization",
        "cookie",
        "token",
        "jwt",
        "password",
        "secret",
        "credential",
        "apikey",
        "api_key",
        "api-key",
        "session",
        "csrf",
        "xsrf",
        "privatekey",
        "private_key",
    )
