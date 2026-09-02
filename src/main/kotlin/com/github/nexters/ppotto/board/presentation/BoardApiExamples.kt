package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailV2Response
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutV2Request
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.DrawingChangesRequest
import com.github.nexters.ppotto.board.presentation.dto.DrawingChangesV2Request
import com.github.nexters.ppotto.board.presentation.dto.DrawingCreateRequest
import com.github.nexters.ppotto.board.presentation.dto.DrawingCreateV2Request
import com.github.nexters.ppotto.board.presentation.dto.DrawingResponse
import com.github.nexters.ppotto.board.presentation.dto.DrawingV2Response
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.StickerLayoutRequest
import com.github.nexters.ppotto.board.presentation.dto.StickerResponse
import com.github.nexters.ppotto.board.presentation.dto.withLegacyZIndex
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private val BOARD_ID = BoardId(UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b"))
private val CREATED_BOARD_ID = BoardId(UUID.fromString("01983f2a-4d5e-7f6a-b7c8-9d0e1f2a3b4c"))
private val STICKER_ID = StickerId(UUID.fromString("01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))
private val DELETED_DRAWING_ID = DrawingId(UUID.fromString("01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a"))

private val TEXT_DRAWING_ID = DrawingId(UUID.fromString("01983f2c-5e6f-7a8b-9c0d-1e2f3a4b5c6d"))
private val STROKE_DRAWING_ID = DrawingId(UUID.fromString("01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))

private val STICKER_STROKE = mapOf<String, Any?>("points" to listOf(listOf(10.5, 22), listOf(14.2, 25.1), listOf(19.8, 27.4)))
private val BOARD_STROKE = mapOf<String, Any?>("points" to listOf(listOf(120, 480.5), listOf(126.4, 483.2), listOf(133.1, 481)))

private val LEGACY_STICKER_STROKE = STICKER_STROKE.withLegacyZIndex(6)
private val LEGACY_BOARD_STROKE = BOARD_STROKE.withLegacyZIndex(4)

private val MOVED_STICKER_LAYOUT =
    StickerLayoutRequest(
        id = STICKER_ID,
        title = null,
        posX = 80.0,
        posY = 290.5,
        scale = 1.1,
        rotation = -8.0,
        zIndex = 6,
        badgeOffsetX = -24.0,
        badgeOffsetY = 96.0,
        badgeRotation = 0.0,
    )

private val STICKER_MOVE_LAYOUT_REQUEST =
    ApiExample(
        name = "스티커 이동 모드 종료",
        value = BoardLayoutRequest(stickers = listOf(MOVED_STICKER_LAYOUT)),
    )

private val TEXT_MODE_LAYOUT_REQUEST =
    ApiExample(
        name = "텍스트 모드 종료 (제목 + 뱃지 배치)",
        value =
            BoardLayoutRequest(
                stickers =
                    listOf(
                        MOVED_STICKER_LAYOUT.copy(
                            title = "고양이 모음집",
                            badgeOffsetX = -30.0,
                            badgeOffsetY = 102.0,
                            badgeRotation = 6.0,
                        ),
                    ),
            ),
    )

private val DRAWING_MODE_LAYOUT_REQUEST =
    ApiExample(
        name = "드로잉 모드 종료 (생성 2건, 삭제 1건)",
        value =
            BoardLayoutRequest(
                drawings =
                    DrawingChangesRequest(
                        created =
                            listOf(
                                DrawingCreateRequest(
                                    id = DrawingId(UUID.fromString("01983f2c-3c4d-7e5f-a6b7-8c9d0e1f2a3b")),
                                    scope = DrawingScope.STICKER,
                                    stickerId = STICKER_ID,
                                    stroke = LEGACY_STICKER_STROKE,
                                    color = "#FFD400",
                                    strokeWidth = 4.0,
                                ),
                                DrawingCreateRequest(
                                    id = DrawingId(UUID.fromString("01983f2c-4d5e-7f6a-b7c8-9d0e1f2a3b4c")),
                                    scope = DrawingScope.BOARD,
                                    stickerId = null,
                                    stroke = mapOf<String, Any?>("points" to listOf(listOf(200, 512))).withLegacyZIndex(7),
                                    color = "#FF5A5A",
                                    strokeWidth = 3.0,
                                ),
                            ),
                        deletedIds = listOf(DELETED_DRAWING_ID),
                    ),
            ),
    )

private val BOARD_LIST_RESPONSE =
    ApiExample(
        name = "보드 목록",
        value =
            ApiResponse.success(
                listOf(
                    BoardResponse(id = BOARD_ID, name = "Board 7"),
                    BoardResponse(id = CREATED_BOARD_ID, name = "여름 휴가"),
                ),
            ),
    )

private val CREATE_BOARD_REQUEST = ApiExample(name = "이름 지정", value = CreateBoardRequest(name = "여름 휴가"))

private val CREATED_BOARD_RESPONSE =
    ApiExample(
        name = "생성 완료",
        value = ApiResponse.success(BoardResponse(id = CREATED_BOARD_ID, name = "여름 휴가")),
    )

private val RENAME_BOARD_REQUEST = ApiExample(name = "이름 변경", value = RenameBoardRequest(name = "뽀또의 보드"))

private val RENAMED_BOARD_RESPONSE =
    ApiExample(
        name = "변경 완료",
        value = ApiResponse.success(BoardResponse(id = BOARD_ID, name = "뽀또의 보드")),
    )

private val DETAIL_STICKERS =
    listOf(
        StickerResponse(
            id = STICKER_ID,
            title = "동물 밈 짤줍",
            isNew = false,
            type = BoardStickerType.IMAGE,
            imageUrl =
                "https://storage.googleapis.com/ppotto-stickers/01983f2b.png" +
                    "?X-Goog-Expires=3600&X-Goog-Signature=sample",
            textContent = null,
            posX = 62.5,
            posY = 318.0,
            scale = 0.8,
            rotation = -12.0,
            zIndex = 3,
            badgeOffsetX = -24.0,
            badgeOffsetY = 96.0,
            badgeRotation = 0.0,
        ),
        StickerResponse(
            id = StickerId(UUID.fromString("01983f2b-3c4d-7e5f-a6b7-8c9d0e1f2a3b")),
            title = "언제까지 일해요",
            isNew = true,
            type = BoardStickerType.TEXT,
            imageUrl = null,
            textContent = "whats in my mac",
            posX = 228.0,
            posY = 250.5,
            scale = 1.0,
            rotation = 8.5,
            zIndex = 5,
            badgeOffsetX = 12.0,
            badgeOffsetY = -60.0,
            badgeRotation = -4.0,
        ),
    )

private val BOARD_DETAIL_RESPONSE =
    ApiExample(
        name = "스티커와 그림이 있는 보드",
        value =
            ApiResponse.success(
                BoardDetailResponse(
                    id = BOARD_ID,
                    name = "Board 7",
                    stickers = DETAIL_STICKERS,
                    drawings =
                        listOf(
                            DrawingResponse(
                                id = STROKE_DRAWING_ID,
                                scope = DrawingScope.STICKER,
                                stickerId = STICKER_ID,
                                stroke = LEGACY_STICKER_STROKE,
                                color = "#FFD400",
                                strokeWidth = 4.0,
                            ),
                            DrawingResponse(
                                id = DELETED_DRAWING_ID,
                                scope = DrawingScope.BOARD,
                                stickerId = null,
                                stroke = LEGACY_BOARD_STROKE,
                                color = "#FFFFFF",
                                strokeWidth = 2.5,
                            ),
                        ),
                ),
            ),
    )

private val STICKER_MOVE_LAYOUT_V2_REQUEST =
    ApiExample(
        name = "스티커 이동 모드 종료",
        value = BoardLayoutV2Request(stickers = listOf(MOVED_STICKER_LAYOUT)),
    )

private val DRAWING_MODE_LAYOUT_V2_REQUEST =
    ApiExample(
        name = "드로잉 모드 종료 (선 생성 1건, 삭제 1건)",
        value =
            BoardLayoutV2Request(
                drawings =
                    DrawingChangesV2Request(
                        created =
                            listOf(
                                DrawingCreateV2Request.Stroke(
                                    id = STROKE_DRAWING_ID,
                                    scope = DrawingScope.STICKER,
                                    stickerId = STICKER_ID,
                                    color = "#FFD400",
                                    zIndex = 6,
                                    stroke = STICKER_STROKE,
                                    strokeWidth = 4.0,
                                ),
                            ),
                        deletedIds = listOf(DELETED_DRAWING_ID),
                    ),
            ),
    )

private val TEXT_MODE_LAYOUT_V2_REQUEST =
    ApiExample(
        name = "텍스트 모드 종료 (텍스트 생성 1건)",
        summary = "content 는 클라이언트가 실측한 줄바꿈이 들어간 문구, maxWidth 는 그 줄바꿈 기준이 된 편집창 폭",
        value =
            BoardLayoutV2Request(
                drawings =
                    DrawingChangesV2Request(
                        created =
                            listOf(
                                DrawingCreateV2Request.Text(
                                    id = TEXT_DRAWING_ID,
                                    scope = DrawingScope.BOARD,
                                    stickerId = null,
                                    color = "#FFFFFF",
                                    zIndex = 7,
                                    content = "여름 휴가",
                                    fontSize = 26.0,
                                    posX = 80.0,
                                    posY = 290.5,
                                    maxWidth = 280.0,
                                    rotation = 0.0,
                                ),
                            ),
                    ),
            ),
    )

private val BOARD_DETAIL_V2_RESPONSE =
    ApiExample(
        name = "스티커와 선, 텍스트가 있는 보드",
        value =
            ApiResponse.success(
                BoardDetailV2Response(
                    id = BOARD_ID,
                    name = "Board 7",
                    stickers = DETAIL_STICKERS,
                    drawings =
                        listOf(
                            DrawingV2Response.Stroke(
                                id = STROKE_DRAWING_ID,
                                scope = DrawingScope.STICKER,
                                stickerId = STICKER_ID,
                                color = "#FFD400",
                                zIndex = 6,
                                stroke = STICKER_STROKE,
                                strokeWidth = 4.0,
                            ),
                            DrawingV2Response.Text(
                                id = TEXT_DRAWING_ID,
                                scope = DrawingScope.BOARD,
                                stickerId = null,
                                color = "#FFFFFF",
                                zIndex = 7,
                                content = "여름 휴가",
                                fontSize = 26.0,
                                posX = 80.0,
                                posY = 290.5,
                                maxWidth = 280.0,
                                rotation = 0.0,
                            ),
                        ),
                ),
            ),
    )

private val INVALID_LAYOUT =
    ApiExamples.errorExample(
        code = "BOARD-001",
        summary = "소유하지 않은 항목 포함. 부분 저장 없이 전체 거부",
        message = "편집 대상에 소유하지 않은 항목이 포함되어 있습니다.",
    )

private val BOARD_NOT_FOUND_RESPONSE =
    listOf(
        ApiExamples.errorExample(
            code = "BOARD-002",
            summary = "보드 없음 또는 소유자 불일치",
            message = "보드를 찾을 수 없습니다.",
        ),
    )

private val COUNT_LIMIT_EXCEEDED =
    ApiExamples.errorExample(
        code = "BOARD-003",
        summary = "보드 개수 제한(100개) 초과",
        message = "보드는 최대 100개까지 만들 수 있습니다.",
    )

private val LAST_BOARD_CANNOT_BE_DELETED =
    ApiExamples.errorExample(
        code = "BOARD-004",
        summary = "마지막 보드는 삭제 불가",
        message = "마지막 보드는 삭제할 수 없습니다.",
    )

private val ACTIVE_ANALYSIS_EXISTS =
    ApiExamples.errorExample(
        code = "BOARD-005",
        summary = "진행 중인 분석이 이 보드를 대상으로 함",
        message = "분석이 진행 중인 보드는 삭제할 수 없습니다.",
    )

@Component
class BoardApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            BoardApi::list to OperationExamples(responses = mapOf("200" to listOf(BOARD_LIST_RESPONSE))),
            BoardApi::create to
                OperationExamples(
                    request = listOf(CREATE_BOARD_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(CREATED_BOARD_RESPONSE),
                            "400" to
                                listOf(
                                    ApiExamples.INVALID_INPUT_WITH_FIELD_ERRORS.copy(
                                        name = "COMMON-001",
                                        summary = "이름 형식 오류 (10자 초과 등)",
                                    ),
                                    COUNT_LIMIT_EXCEEDED,
                                ),
                            "409" to ApiExamples.CONFLICT_RESPONSE,
                        ),
                ),
            BoardDetailApi::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(BOARD_DETAIL_RESPONSE),
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                        ),
                ),
            BoardApi::rename to
                OperationExamples(
                    request = listOf(RENAME_BOARD_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(RENAMED_BOARD_RESPONSE),
                            "400" to ApiExamples.INVALID_INPUT_RESPONSE,
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                        ),
                ),
            BoardApi::delete to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                            "409" to listOf(LAST_BOARD_CANNOT_BE_DELETED, ACTIVE_ANALYSIS_EXISTS),
                        ),
                ),
            BoardLayoutApi::update to
                OperationExamples(
                    request =
                        listOf(STICKER_MOVE_LAYOUT_REQUEST, TEXT_MODE_LAYOUT_REQUEST, DRAWING_MODE_LAYOUT_REQUEST),
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "400" to
                                listOf(
                                    ApiExamples.INVALID_INPUT.copy(summary = "필드 형식 오류 (제목 15자 초과 등)"),
                                    INVALID_LAYOUT,
                                ),
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                        ),
                ),
            BoardDetailV2Api::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(BOARD_DETAIL_V2_RESPONSE),
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                        ),
                ),
            BoardLayoutV2Api::update to
                OperationExamples(
                    request =
                        listOf(
                            STICKER_MOVE_LAYOUT_V2_REQUEST,
                            DRAWING_MODE_LAYOUT_V2_REQUEST,
                            TEXT_MODE_LAYOUT_V2_REQUEST,
                        ),
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "400" to
                                listOf(
                                    ApiExamples.INVALID_INPUT.copy(summary = "필드 형식 오류 (문구 32자 초과, 색상 형식 오류 등)"),
                                    INVALID_LAYOUT,
                                ),
                            "404" to BOARD_NOT_FOUND_RESPONSE,
                        ),
                ),
        )
}
