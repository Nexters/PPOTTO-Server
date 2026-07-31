package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/terms", version = "1")
class TermsController(
    private val termsService: TermsService,
) {
    @GetMapping
    fun findCurrentTerms(
        @AuthenticationPrincipal userId: UUID?,
    ): ApiResponse<List<TermResponse>> =
        termsService
            .findCurrentTerms(userId ?: throw UnauthorizedException())
            .map(TermResponse::from)
            .let { ApiResponse.success(it) }

    @PostMapping("/agreements")
    fun agree(
        @AuthenticationPrincipal userId: UUID?,
        @Valid @RequestBody request: AgreeTermsRequest,
    ): ApiResponse<Unit> =
        termsService
            .agree(userId ?: throw UnauthorizedException(), request.termIds)
            .let { ApiResponse.success() }
}
