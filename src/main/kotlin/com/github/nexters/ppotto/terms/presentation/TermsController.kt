package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.identifier.TermId
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
import java.util.UUID

@RestController
class TermsController(
    private val termsService: TermsService,
) : TermsApi {
    override fun findCurrentTerms(
        @CurrentUser userId: UUID?,
    ): ApiResponse<List<TermResponse>> =
        termsService
            .findCurrentTerms(userId?.let(::UserId))
            .map(TermResponse::from)
            .let { ApiResponse.success(it) }

    override fun agree(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: AgreeTermsRequest,
    ): ApiResponse<Unit> =
        termsService
            .agree(UserId(userId), request.termIds.map(::TermId))
            .let { ApiResponse.success() }
}
