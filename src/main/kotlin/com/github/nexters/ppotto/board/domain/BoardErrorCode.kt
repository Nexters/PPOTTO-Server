package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class BoardErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_LAYOUT(HttpStatus.BAD_REQUEST, "BOARD-001", "편집 대상에 소유하지 않은 항목이 포함되어 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "BOARD-002", "보드를 찾을 수 없습니다."),
    COUNT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "BOARD-003", "보드는 최대 ${Board.MAX_COUNT}개까지 만들 수 있습니다."),
    LAST_BOARD_CANNOT_BE_DELETED(HttpStatus.CONFLICT, "BOARD-004", "마지막 보드는 삭제할 수 없습니다."),
    ACTIVE_ANALYSIS_EXISTS(HttpStatus.CONFLICT, "BOARD-005", "분석이 진행 중인 보드는 삭제할 수 없습니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "BOARD-006", "초대를 찾을 수 없습니다."),
    INVALID_INVITATION_PREVIEW(HttpStatus.BAD_REQUEST, "BOARD-007", "초대 미리보기 이미지가 올바르지 않습니다."),
    INVITATION_CODE_RENEWED(HttpStatus.CONFLICT, "BOARD-008", "초대 코드가 갱신되었습니다."),
    PARTICIPANT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "BOARD-009", "보드 참여 인원이 가득 찼습니다."),
}
