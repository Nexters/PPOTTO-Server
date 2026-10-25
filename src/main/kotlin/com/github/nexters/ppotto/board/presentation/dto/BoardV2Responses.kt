package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.nexters.ppotto.board.application.model.BoardDetail
import com.github.nexters.ppotto.board.application.model.BoardSummary
import com.github.nexters.ppotto.board.application.port.BoardAnalysisStatus
import com.github.nexters.ppotto.board.domain.BoardKind
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "보드와 배치된 스티커, 선, 텍스트 (v2)")
data class BoardDetailV2Response(
    @get:Schema(description = "보드 ID (uuidv7)", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    @get:JsonProperty("id")
    val id: BoardId,

    @field:Schema(description = "보드 이름", example = "Board 7")
    val name: String,

    @field:Schema(description = "보드에 배치된 스티커 목록. v1과 동일")
    val stickers: List<BoardStickerResponse>,

    @field:Schema(description = "보드와 스티커 위의 선과 텍스트 목록")
    val drawings: List<DrawingV2Response>,
) {
    companion object {
        fun from(board: BoardDetail): BoardDetailV2Response =
            BoardDetailV2Response(
                id = board.id,
                name = board.name,
                stickers = board.stickers.map(BoardStickerResponse::from),
                drawings = board.drawings.map(DrawingV2Response::from),
            )
    }
}

@Schema(
    description = "보드 또는 스티커 위의 선이나 텍스트. type이 판별자다",
    discriminatorProperty = "type",
    oneOf = [DrawingV2Response.Stroke::class, DrawingV2Response.Text::class],
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DrawingV2Response.Stroke::class, name = "STROKE"),
    JsonSubTypes.Type(value = DrawingV2Response.Text::class, name = "TEXT"),
)
sealed interface DrawingV2Response {
    val id: DrawingId
    val scope: DrawingScope
    val stickerId: StickerId?
    val color: String
    val zIndex: Int

    @Schema(name = "DrawingStrokeResponse", description = "선")
    data class Stroke(
        @get:Schema(description = "그림 ID (uuidv7)", example = "01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "선이 붙는 대상", example = "STICKER")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때만 값이 있음", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId?,

        @field:Schema(description = "선 색상", example = "#FFD400")
        override val color: String,

        @get:Schema(description = "겹침 순서", example = "6")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:Schema(
            description = "선 데이터. 저장한 그대로 내려감",
            example = "{\"points\":[[10.5,22],[14.2,25.1],[19.8,27.4]]}",
        )
        val stroke: Map<String, Any?>,

        @field:Schema(description = "선 굵기", example = "4")
        val strokeWidth: Double,
    ) : DrawingV2Response

    @Schema(name = "DrawingTextResponse", description = "텍스트")
    data class Text(
        @get:Schema(description = "그림 ID (uuidv7)", example = "01983f2c-5e6f-7a8b-9c0d-1e2f3a4b5c6d")
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "텍스트가 붙는 대상", example = "BOARD")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때만 값이 있음", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId?,

        @field:Schema(description = "글자 색상", example = "#FFFFFF")
        override val color: String,

        @get:Schema(description = "겹침 순서", example = "7")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:Schema(description = "표시할 문구. 저장된 줄바꿈이 그대로 내려감", example = "여름 휴가")
        val content: String,

        @field:Schema(description = "글자 크기", example = "26")
        val fontSize: Double,

        @field:Schema(description = "보드 좌표 X. 텍스트 상자의 중심", example = "80")
        val posX: Double,

        @field:Schema(description = "보드 좌표 Y. 텍스트 상자의 중심", example = "290.5")
        val posY: Double,

        @field:Schema(description = "줄바꿈 기준이 된 편집창 폭", example = "280")
        val maxWidth: Double,

        @field:Schema(description = "회전 각도(degree)", example = "0")
        val rotation: Double,
    ) : DrawingV2Response

    companion object {
        fun from(drawing: Drawing): DrawingV2Response =
            when (drawing) {
                is Drawing.Stroke ->
                    Stroke(
                        id = drawing.id,
                        scope = drawing.scope,
                        stickerId = drawing.stickerId,
                        color = drawing.color,
                        zIndex = drawing.zIndex,
                        stroke = drawing.stroke,
                        strokeWidth = drawing.strokeWidth,
                    )

                is Drawing.Text ->
                    Text(
                        id = drawing.id,
                        scope = drawing.scope,
                        stickerId = drawing.stickerId,
                        color = drawing.color,
                        zIndex = drawing.zIndex,
                        content = drawing.content,
                        fontSize = drawing.fontSize,
                        posX = drawing.posX,
                        posY = drawing.posY,
                        maxWidth = drawing.maxWidth,
                        rotation = drawing.rotation,
                    )
            }
    }
}

@Schema(description = "홈 보드 목록과 탭별 개수 (v2)")
data class BoardListV2Response(
    @field:Schema(description = "탭별 보드 개수")
    val counts: BoardCountsResponse,

    @field:Schema(description = "보드 카드 목록. lastActivityAt 내림차순, 같으면 id 내림차순")
    val boards: List<BoardSummaryV2Response>,
) {
    companion object {
        fun from(boards: List<BoardSummary>): BoardListV2Response =
            BoardListV2Response(
                counts =
                    BoardCountsResponse(
                        all = boards.size,
                        personal = boards.count { it.kind == BoardKind.PERSONAL },
                        shared = boards.count { it.kind == BoardKind.SHARED },
                    ),
                boards = boards.map(BoardSummaryV2Response::from),
            )
    }
}

@Schema(description = "탭별 보드 개수")
data class BoardCountsResponse(
    @field:Schema(description = "전체 탭. personal + shared", example = "3")
    val all: Int,

    @field:Schema(description = "개인 탭", example = "2")
    val personal: Int,

    @field:Schema(description = "공동 탭", example = "1")
    val shared: Int,
)

@Schema(description = "홈 보드 카드 (v2)")
data class BoardSummaryV2Response(
    @get:Schema(description = "보드 ID (uuidv7)", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    @get:JsonProperty("id")
    val id: BoardId,

    @field:Schema(description = "보드 이름", example = "여름 휴가")
    val name: String,

    @field:Schema(description = "보드 종류. 참여자가 한 명이라도 있으면 SHARED", example = "SHARED")
    val kind: BoardKind,

    @field:Schema(description = "요청한 사용자의 역할. 방장이면 OWNER, 참여자면 MEMBER", example = "OWNER")
    val role: BoardRole,

    @field:Schema(description = "삭제되지 않은 스티커 수. 이미지형과 텍스트형을 모두 센다", example = "12")
    val stickerCount: Int,

    @field:Schema(description = "참여 인원. 방장 1명 + 참여자 수", example = "3")
    val participantCount: Int,

    @field:Schema(description = "최근 이미지형 스티커의 읽기용 signed URL. 최신순 최대 4개이며 없으면 빈 배열 (만료 1시간)")
    val previewImageUrls: List<String>,

    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    @field:Schema(description = "가장 최근 이미지형 스티커의 메인 컬러. 이미지형 스티커가 없으면 null", example = "#FF6B6B")
    val mainColor: String?,

    @field:Schema(
        description = "마지막 활동 시각. 삭제되지 않은 스티커의 최근 생성·수정 시각이며 스티커가 없으면 보드 생성 시각",
        example = "2026-10-20T12:40:05Z",
    )
    val lastActivityAt: Instant,

    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    @field:Schema(description = "이 보드에서 진행 중인 내 분석의 상태. 다른 참여자의 분석은 보이지 않으며 없으면 null", example = "ANALYZING")
    val analysisStatus: BoardAnalysisStatus?,
) {
    companion object {
        fun from(board: BoardSummary): BoardSummaryV2Response =
            BoardSummaryV2Response(
                id = board.id,
                name = board.name,
                kind = board.kind,
                role = board.role,
                stickerCount = board.stickerCount,
                participantCount = board.participantCount,
                previewImageUrls = board.previewImageUrls,
                mainColor = board.mainColor,
                lastActivityAt = board.lastActivityAt,
                analysisStatus = board.analysisStatus,
            )
    }
}
