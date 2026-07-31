package com.github.nexters.ppotto.analysis.presentation.dto

object AnalysisApiExamples {
    const val CREATE_ANALYSIS_REQUEST: String =
        """
        {
          "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
          "photos": [
            {
              "takenAt": "2026-06-14T13:22:10+09:00",
              "contentType": "image/jpeg"
            },
            {
              "takenAt": "2026-06-14T13:24:02+09:00",
              "contentType": "image/heic"
            },
            {
              "takenAt": "2026-07-02T19:05:44+09:00",
              "contentType": "image/jpeg"
            }
          ]
        }
        """

    const val CREATE_ANALYSIS_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "analysisId": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "uploads": [
              {
                "photoId": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "uploadUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Expires=900"
              },
              {
                "photoId": "01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
                "uploadUrl": "https://storage.googleapis.com/ppotto-photos/01983f2f.heic?X-Goog-Expires=900"
              }
            ]
          },
          "error": null
        }
        """

    const val ANALYZING_STATUS_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            "status": "ANALYZING",
            "progress": 45,
            "failedReason": null,
            "startedAt": "2026-07-27T14:02:11+09:00",
            "completedAt": null
          },
          "error": null
        }
        """

    const val COMPLETED_STATUS_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            "status": "COMPLETED",
            "progress": 100,
            "failedReason": null,
            "startedAt": "2026-07-27T14:02:11+09:00",
            "completedAt": "2026-07-27T14:03:38+09:00"
          },
          "error": null
        }
        """

    const val FAILED_STATUS_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            "status": "FAILED",
            "progress": 60,
            "failedReason": "AI 분석 호출이 반복 실패했습니다.",
            "startedAt": "2026-07-27T14:02:11+09:00",
            "completedAt": null
          },
          "error": null
        }
        """

    const val NO_ACTIVE_ANALYSIS_RESPONSE: String =
        """
        {
          "success": true,
          "data": null,
          "error": null
        }
        """

    const val START_UPLOAD_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "uploadedCount": 97,
            "failedCount": 1,
            "failedPhotoIds": [
              "01983f2e-9f8e-7d6c-b5a4-3c2b1a0f9e8d"
            ]
          },
          "error": null
        }
        """

    const val BOARD_NOT_FOUND: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "BOARD-002",
            "message": "보드를 찾을 수 없습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val PHOTO_COUNT_OUT_OF_RANGE: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ANALYSIS-001",
            "message": "사진은 90장에서 100장 사이여야 합니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val ACTIVE_ANALYSIS_EXISTS: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ANALYSIS-002",
            "message": "이미 진행 중인 분석이 있습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val ALREADY_STARTED_OR_FINISHED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ANALYSIS-003",
            "message": "이미 시작되었거나 종료된 분석입니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val ANALYSIS_NOT_FOUND: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ANALYSIS-005",
            "message": "분석을 찾을 수 없습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val NO_UPLOADED_PHOTOS: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ANALYSIS-008",
            "message": "업로드된 사진이 없습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
