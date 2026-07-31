package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisApiExamples
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.auth.presentation.dto.AuthApiExamples
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardApiExamples
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.StickerApiExamples
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import com.github.nexters.ppotto.terms.presentation.dto.TermsApiExamples
import com.github.nexters.ppotto.user.presentation.dto.UserApiExamples
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldNotBe
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

private val strictMapper: JsonMapper =
    JsonMapper
        .builder()
        .findAndAddModules()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build()

private inline fun <reified T> example(
    name: String,
    json: String,
): Pair<String, () -> Any?> = name to { strictMapper.readValue(json, object : TypeReference<T>() {}) }

private val requestExamples: List<Pair<String, () -> Any?>> =
    listOf(
        example<LoginRequest>("AuthApiExamples.KAKAO_LOGIN_REQUEST", AuthApiExamples.KAKAO_LOGIN_REQUEST),
        example<LoginRequest>("AuthApiExamples.APPLE_FIRST_LOGIN_REQUEST", AuthApiExamples.APPLE_FIRST_LOGIN_REQUEST),
        example<LoginRequest>("AuthApiExamples.APPLE_RELOGIN_REQUEST", AuthApiExamples.APPLE_RELOGIN_REQUEST),
        example<AgreeTermsRequest>("TermsApiExamples.AGREE_TERMS_REQUEST", TermsApiExamples.AGREE_TERMS_REQUEST),
        example<CreateBoardRequest>("BoardApiExamples.CREATE_BOARD_REQUEST", BoardApiExamples.CREATE_BOARD_REQUEST),
        example<RenameBoardRequest>("BoardApiExamples.RENAME_BOARD_REQUEST", BoardApiExamples.RENAME_BOARD_REQUEST),
        example<BoardLayoutRequest>(
            "BoardApiExamples.STICKER_MOVE_LAYOUT_REQUEST",
            BoardApiExamples.STICKER_MOVE_LAYOUT_REQUEST,
        ),
        example<BoardLayoutRequest>(
            "BoardApiExamples.TEXT_MODE_LAYOUT_REQUEST",
            BoardApiExamples.TEXT_MODE_LAYOUT_REQUEST,
        ),
        example<BoardLayoutRequest>(
            "BoardApiExamples.DRAWING_MODE_LAYOUT_REQUEST",
            BoardApiExamples.DRAWING_MODE_LAYOUT_REQUEST,
        ),
        example<UpdateStickerTitleRequest>(
            "StickerApiExamples.UPDATE_STICKER_TITLE_REQUEST",
            StickerApiExamples.UPDATE_STICKER_TITLE_REQUEST,
        ),
        example<CreateAnalysisRequest>(
            "AnalysisApiExamples.CREATE_ANALYSIS_REQUEST",
            AnalysisApiExamples.CREATE_ANALYSIS_REQUEST,
        ),
    )

private val successResponseExamples: List<Pair<String, () -> Any?>> =
    listOf(
        example<ApiResponse<Any>>("ApiExamples.SUCCESS_EMPTY", ApiExamples.SUCCESS_EMPTY),
        example<ApiResponse<LoginResponse>>(
            "AuthApiExamples.NEW_USER_LOGIN_RESPONSE",
            AuthApiExamples.NEW_USER_LOGIN_RESPONSE,
        ),
        example<ApiResponse<LoginResponse>>(
            "AuthApiExamples.RETURNING_USER_LOGIN_RESPONSE",
            AuthApiExamples.RETURNING_USER_LOGIN_RESPONSE,
        ),
        example<ApiResponse<TokenPairResponse>>(
            "AuthApiExamples.TOKEN_PAIR_RESPONSE",
            AuthApiExamples.TOKEN_PAIR_RESPONSE,
        ),
        example<ApiResponse<UserResponse>>(
            "UserApiExamples.KAKAO_USER_RESPONSE",
            UserApiExamples.KAKAO_USER_RESPONSE,
        ),
        example<ApiResponse<UserResponse>>(
            "UserApiExamples.APPLE_PRIVATE_RELAY_USER_RESPONSE",
            UserApiExamples.APPLE_PRIVATE_RELAY_USER_RESPONSE,
        ),
        example<ApiResponse<List<TermResponse>>>(
            "TermsApiExamples.CURRENT_TERMS_RESPONSE",
            TermsApiExamples.CURRENT_TERMS_RESPONSE,
        ),
        example<ApiResponse<List<TermResponse>>>(
            "TermsApiExamples.ANONYMOUS_TERMS_RESPONSE",
            TermsApiExamples.ANONYMOUS_TERMS_RESPONSE,
        ),
        example<ApiResponse<List<BoardResponse>>>(
            "BoardApiExamples.BOARD_LIST_RESPONSE",
            BoardApiExamples.BOARD_LIST_RESPONSE,
        ),
        example<ApiResponse<BoardResponse>>(
            "BoardApiExamples.CREATED_BOARD_RESPONSE",
            BoardApiExamples.CREATED_BOARD_RESPONSE,
        ),
        example<ApiResponse<BoardResponse>>(
            "BoardApiExamples.RENAMED_BOARD_RESPONSE",
            BoardApiExamples.RENAMED_BOARD_RESPONSE,
        ),
        example<ApiResponse<BoardDetailResponse>>(
            "BoardApiExamples.BOARD_DETAIL_RESPONSE",
            BoardApiExamples.BOARD_DETAIL_RESPONSE,
        ),
        example<ApiResponse<RecapDetailResponse>>(
            "StickerApiExamples.RECAP_DETAIL_RESPONSE",
            StickerApiExamples.RECAP_DETAIL_RESPONSE,
        ),
        example<ApiResponse<UpdateStickerTitleResponse>>(
            "StickerApiExamples.UPDATE_STICKER_TITLE_RESPONSE",
            StickerApiExamples.UPDATE_STICKER_TITLE_RESPONSE,
        ),
        example<ApiResponse<CreateAnalysisResponse>>(
            "AnalysisApiExamples.CREATE_ANALYSIS_RESPONSE",
            AnalysisApiExamples.CREATE_ANALYSIS_RESPONSE,
        ),
        example<ApiResponse<AnalysisStatusResponse>>(
            "AnalysisApiExamples.ANALYZING_STATUS_RESPONSE",
            AnalysisApiExamples.ANALYZING_STATUS_RESPONSE,
        ),
        example<ApiResponse<AnalysisStatusResponse>>(
            "AnalysisApiExamples.COMPLETED_STATUS_RESPONSE",
            AnalysisApiExamples.COMPLETED_STATUS_RESPONSE,
        ),
        example<ApiResponse<AnalysisStatusResponse>>(
            "AnalysisApiExamples.FAILED_STATUS_RESPONSE",
            AnalysisApiExamples.FAILED_STATUS_RESPONSE,
        ),
        example<ApiResponse<Any>>(
            "AnalysisApiExamples.NO_ACTIVE_ANALYSIS_RESPONSE",
            AnalysisApiExamples.NO_ACTIVE_ANALYSIS_RESPONSE,
        ),
        example<ApiResponse<StartUploadResponse>>(
            "AnalysisApiExamples.START_UPLOAD_RESPONSE",
            AnalysisApiExamples.START_UPLOAD_RESPONSE,
        ),
    )

private val errorResponseExamples: List<Pair<String, () -> Any?>> =
    listOf(
        example<ApiErrorResponse>("ApiExamples.INVALID_INPUT", ApiExamples.INVALID_INPUT),
        example<ApiErrorResponse>(
            "ApiExamples.INVALID_INPUT_WITH_FIELD_ERRORS",
            ApiExamples.INVALID_INPUT_WITH_FIELD_ERRORS,
        ),
        example<ApiErrorResponse>("ApiExamples.UNAUTHORIZED", ApiExamples.UNAUTHORIZED),
        example<ApiErrorResponse>("ApiExamples.CONFLICT", ApiExamples.CONFLICT),
        example<ApiErrorResponse>(
            "AuthApiExamples.SOCIAL_AUTHENTICATION_FAILED",
            AuthApiExamples.SOCIAL_AUTHENTICATION_FAILED,
        ),
        example<ApiErrorResponse>(
            "AuthApiExamples.APPLE_CODE_EXCHANGE_FAILED",
            AuthApiExamples.APPLE_CODE_EXCHANGE_FAILED,
        ),
        example<ApiErrorResponse>(
            "AuthApiExamples.KAKAO_EMAIL_CONSENT_REQUIRED",
            AuthApiExamples.KAKAO_EMAIL_CONSENT_REQUIRED,
        ),
        example<ApiErrorResponse>("AuthApiExamples.INVALID_REFRESH_TOKEN", AuthApiExamples.INVALID_REFRESH_TOKEN),
        example<ApiErrorResponse>("TermsApiExamples.REQUIRED_TERMS_MISSING", TermsApiExamples.REQUIRED_TERMS_MISSING),
        example<ApiErrorResponse>("BoardApiExamples.INVALID_LAYOUT", BoardApiExamples.INVALID_LAYOUT),
        example<ApiErrorResponse>("BoardApiExamples.BOARD_NOT_FOUND", BoardApiExamples.BOARD_NOT_FOUND),
        example<ApiErrorResponse>("BoardApiExamples.COUNT_LIMIT_EXCEEDED", BoardApiExamples.COUNT_LIMIT_EXCEEDED),
        example<ApiErrorResponse>(
            "BoardApiExamples.LAST_BOARD_CANNOT_BE_DELETED",
            BoardApiExamples.LAST_BOARD_CANNOT_BE_DELETED,
        ),
        example<ApiErrorResponse>("BoardApiExamples.ACTIVE_ANALYSIS_EXISTS", BoardApiExamples.ACTIVE_ANALYSIS_EXISTS),
        example<ApiErrorResponse>("StickerApiExamples.STICKER_NOT_FOUND", StickerApiExamples.STICKER_NOT_FOUND),
        example<ApiErrorResponse>("AnalysisApiExamples.BOARD_NOT_FOUND", AnalysisApiExamples.BOARD_NOT_FOUND),
        example<ApiErrorResponse>(
            "AnalysisApiExamples.PHOTO_COUNT_OUT_OF_RANGE",
            AnalysisApiExamples.PHOTO_COUNT_OUT_OF_RANGE,
        ),
        example<ApiErrorResponse>(
            "AnalysisApiExamples.ACTIVE_ANALYSIS_EXISTS",
            AnalysisApiExamples.ACTIVE_ANALYSIS_EXISTS,
        ),
        example<ApiErrorResponse>(
            "AnalysisApiExamples.ALREADY_STARTED_OR_FINISHED",
            AnalysisApiExamples.ALREADY_STARTED_OR_FINISHED,
        ),
        example<ApiErrorResponse>("AnalysisApiExamples.ANALYSIS_NOT_FOUND", AnalysisApiExamples.ANALYSIS_NOT_FOUND),
        example<ApiErrorResponse>("AnalysisApiExamples.NO_UPLOADED_PHOTOS", AnalysisApiExamples.NO_UPLOADED_PHOTOS),
    )

class OpenApiExampleContractTest :
    BehaviorSpec({
        Given("Swagger @ExampleObject로 노출하는 예시 JSON이 주어졌을 때") {
            When("미지 필드를 실패로 처리하는 strict 설정으로 요청 DTO에 역직렬화하면") {
                Then("모든 요청 예시가 실제 요청 DTO 계약과 일치한다") {
                    requestExamples.forEach { (name, deserialize) ->
                        withClue(name) { deserialize() shouldNotBe null }
                    }
                }
            }

            When("미지 필드를 실패로 처리하는 strict 설정으로 공통 응답 봉투에 역직렬화하면") {
                Then("모든 성공 응답 예시가 실제 응답 DTO 계약과 일치한다") {
                    successResponseExamples.forEach { (name, deserialize) ->
                        withClue(name) { deserialize() shouldNotBe null }
                    }
                }

                Then("모든 실패 응답 예시가 실제 오류 응답 계약과 일치한다") {
                    errorResponseExamples.forEach { (name, deserialize) ->
                        withClue(name) { deserialize() shouldNotBe null }
                    }
                }
            }

            Then("검증 대상 예시가 누락 없이 등록되어 있다") {
                (requestExamples + successResponseExamples + errorResponseExamples).size shouldBeGreaterThanOrEqual 52
            }
        }
    })
