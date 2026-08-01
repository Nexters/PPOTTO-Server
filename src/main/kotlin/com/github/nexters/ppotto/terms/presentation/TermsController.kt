package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.security.CurrentUser
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TermsController(
    private val termsService: TermsService,
) : TermsApi {
    override fun findCurrentTerms(
        @CurrentUser userId: UserId?,
    ): ApiResponse<List<TermResponse>> =
        termsService
            .findCurrentTerms(userId)
            .map(TermResponse::from)
            .let { ApiResponse.success(it) }

    override fun agree(
        @AuthenticatedUser userId: UserId,
        @Valid @RequestBody request: AgreeTermsRequest,
    ): ApiResponse<Unit> =
        termsService
            .agree(userId, request.termIds)
            .let { ApiResponse.success() }
}
