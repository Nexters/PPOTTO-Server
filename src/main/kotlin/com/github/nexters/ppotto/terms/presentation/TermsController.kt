package com.github.nexters.ppotto.terms.presentation

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
        ApiResponse.success(
            termsService
                .findCurrentTerms(userId)
                .map { TermResponse.from(it) },
        )

    override fun agree(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: AgreeTermsRequest,
    ): ApiResponse<Unit> {
        termsService.agree(userId, request.termIds)
        return ApiResponse.success()
    }
}
