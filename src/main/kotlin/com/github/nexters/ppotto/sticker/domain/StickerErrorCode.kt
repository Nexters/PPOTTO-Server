package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class StickerErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    STICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "STICKER-001", "스티커를 찾을 수 없습니다."),
    STICKER_REGENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "STICKER-002", "이미 재생성이 진행 중입니다."),
    ANALYSIS_STICKER_COUNT_EXCEEDED(
        HttpStatus.BAD_REQUEST,
        "STICKER-003",
        "분석 결과 스티커는 최대 ${Sticker.MAX_ANALYSIS_STICKER_COUNT}개까지 저장할 수 있습니다.",
    ),
    UNEDITABLE_RECAP_COMMENT(HttpStatus.BAD_REQUEST, "STICKER-004", "수정할 수 없는 코멘트가 포함되어 있습니다."),
    NOT_REGENERATABLE_STICKER_TYPE(HttpStatus.BAD_REQUEST, "STICKER-005", "이미지형 스티커만 재생성할 수 있습니다."),
    REGENERATION_PHOTOS_NOT_FOUND(HttpStatus.BAD_REQUEST, "STICKER-006", "재생성할 사진 구성이 없습니다."),
    UNDELETABLE_STICKER(HttpStatus.BAD_REQUEST, "STICKER-007", "삭제할 수 없는 스티커가 포함되어 있습니다."),
    UNEDITABLE_STICKER(HttpStatus.BAD_REQUEST, "STICKER-008", "편집할 수 없는 스티커가 포함되어 있습니다."),
}
