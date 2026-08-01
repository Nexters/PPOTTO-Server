package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private val TOS_ID = TermId(UUID.fromString("01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))
private val PRIVACY_ID = TermId(UUID.fromString("01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a"))

private val TOS_TERM =
    TermResponse(
        id = TOS_ID,
        code = "TOS",
        version = "1.0",
        isRequired = true,
        contentUrl = "https://nexters.notion.site/ppotto-tos",
        agreed = true,
    )

private val CURRENT_TERMS_RESPONSE =
    ApiExample(
        name = "로그인 사용자 - 동의 상태 포함",
        value =
            ApiResponse.success(
                listOf(
                    TOS_TERM,
                    TermResponse(
                        id = PRIVACY_ID,
                        code = "PRIVACY",
                        version = "1.1",
                        isRequired = true,
                        contentUrl = "https://nexters.notion.site/ppotto-privacy",
                        agreed = false,
                    ),
                ),
            ),
    )

private val ANONYMOUS_TERMS_RESPONSE =
    ApiExample(
        name = "비로그인 - agreed는 모두 false",
        value = ApiResponse.success(listOf(TOS_TERM.copy(agreed = false))),
    )

private val AGREE_TERMS_REQUEST =
    ApiExample(
        name = "필수 약관 동의",
        value = AgreeTermsRequest(termIds = listOf(TOS_ID, PRIVACY_ID)),
    )

private val REQUIRED_TERMS_MISSING =
    ApiExamples.errorExample(
        code = "TERM-001",
        summary = "필수 약관 미포함",
        message = "필수 약관에 동의해야 합니다.",
    )

@Component
class TermsApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            TermsApi::findCurrentTerms to
                OperationExamples(
                    responses = mapOf("200" to listOf(CURRENT_TERMS_RESPONSE, ANONYMOUS_TERMS_RESPONSE)),
                ),
            TermsApi::agree to
                OperationExamples(
                    request = listOf(AGREE_TERMS_REQUEST),
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "400" to listOf(ApiExamples.INVALID_INPUT, REQUIRED_TERMS_MISSING),
                        ),
                ),
        )
}
