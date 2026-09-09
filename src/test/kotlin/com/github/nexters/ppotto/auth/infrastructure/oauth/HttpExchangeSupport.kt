package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.sun.net.httpserver.HttpExchange

internal fun HttpExchange.respond(
    status: Int,
    body: String,
) {
    responseHeaders.add("Content-Type", "application/json")
    val bytes = body.toByteArray()
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
