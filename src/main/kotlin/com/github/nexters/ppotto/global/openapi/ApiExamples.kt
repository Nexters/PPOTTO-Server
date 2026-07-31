package com.github.nexters.ppotto.global.openapi

object ApiExamples {
    const val SUCCESS_EMPTY: String =
        """
        {
          "success": true,
          "data": null,
          "error": null
        }
        """

    const val INVALID_INPUT: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "COMMON-001",
            "message": "잘못된 입력입니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val INVALID_INPUT_WITH_FIELD_ERRORS: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "COMMON-001",
            "message": "잘못된 입력입니다.",
            "fieldErrors": [
              {
                "field": "name",
                "value": "열자가넘는아주긴보드이름",
                "reason": "크기가 1에서 10 사이여야 합니다"
              }
            ],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val UNAUTHORIZED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "COMMON-004",
            "message": "인증이 필요합니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val CONFLICT: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "COMMON-006",
            "message": "이미 처리된 요청이거나 충돌이 발생했습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
