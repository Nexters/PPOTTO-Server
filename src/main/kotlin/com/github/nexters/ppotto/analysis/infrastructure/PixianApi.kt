package com.github.nexters.ppotto.analysis.infrastructure

import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.service.annotation.PostExchange
import java.net.URI

interface PixianApi {
    @PostExchange
    fun removeBackground(
        uri: URI,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestPart("image") image: Resource,
        @RequestPart("test") test: String,
        @RequestPart("output.format") outputFormat: String,
    ): ResponseEntity<ByteArray>
}
