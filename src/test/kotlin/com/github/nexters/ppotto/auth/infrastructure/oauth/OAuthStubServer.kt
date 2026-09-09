package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.core.TestConfiguration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.InetSocketAddress
import java.net.URI

internal class OAuthStubServer(
    private val server: HttpServer,
) {
    private val baseUri = "http://localhost:${server.address.port}"

    fun uri(path: String): URI = URI("$baseUri$path")

    fun route(
        path: String,
        handler: (HttpExchange) -> Unit,
    ) {
        server.createContext(path, handler)
    }

    fun <T : Any> api(type: Class<T>): T =
        HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(RestClient.builder().build()))
            .build()
            .createClient(type)
}

internal fun TestConfiguration.stubOAuthServer(routes: OAuthStubServer.() -> Unit): OAuthStubServer {
    val server = HttpServer.create(InetSocketAddress(0), 0)
    val stub = OAuthStubServer(server)
    stub.routes()
    server.start()
    afterSpec { server.stop(0) }
    return stub
}

internal fun HttpExchange.respond(
    status: Int,
    body: String,
) {
    responseHeaders.add("Content-Type", "application/json")
    val bytes = body.toByteArray()
    sendResponseHeaders(status, if (bytes.isEmpty()) NO_BODY else bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private const val NO_BODY = -1L
