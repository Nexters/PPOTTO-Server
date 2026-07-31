package com.github.nexters.ppotto.board.presentation.dto

object BoardApiExamples {
    const val STICKER_MOVE_LAYOUT_REQUEST: String =
        """
        {
          "stickers": [
            {
              "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
              "posX": 80,
              "posY": 290.5,
              "scale": 1.1,
              "rotation": -8,
              "zIndex": 6,
              "badgeOffsetX": -24,
              "badgeOffsetY": 96,
              "badgeRotation": 0
            }
          ]
        }
        """

    const val TEXT_MODE_LAYOUT_REQUEST: String =
        """
        {
          "stickers": [
            {
              "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
              "title": "고양이 모음집",
              "posX": 80,
              "posY": 290.5,
              "scale": 1.1,
              "rotation": -8,
              "zIndex": 6,
              "badgeOffsetX": -30,
              "badgeOffsetY": 102,
              "badgeRotation": 6
            }
          ]
        }
        """

    const val DRAWING_MODE_LAYOUT_REQUEST: String =
        """
        {
          "drawings": {
            "created": [
              {
                "id": "01983f2c-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
                "scope": "STICKER",
                "stickerId": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "stroke": {
                  "points": [[10.5, 22], [14.2, 25.1], [19.8, 27.4]]
                },
                "color": "#FFD400",
                "strokeWidth": 4
              },
              {
                "id": "01983f2c-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
                "scope": "BOARD",
                "stroke": {
                  "points": [[200, 512], [204.8, 515.5]]
                },
                "color": "#FF5A5A",
                "strokeWidth": 3
              }
            ],
            "deletedIds": [
              "01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a"
            ]
          }
        }
        """

    const val BOARD_LIST_RESPONSE: String =
        """
        {
          "success": true,
          "data": [
            {
              "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
              "name": "Board 7"
            },
            {
              "id": "01983f2a-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
              "name": "여름 휴가"
            }
          ],
          "error": null
        }
        """

    const val CREATE_BOARD_REQUEST: String =
        """
        {
          "name": "여름 휴가"
        }
        """

    const val CREATED_BOARD_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2a-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
            "name": "여름 휴가"
          },
          "error": null
        }
        """

    const val RENAME_BOARD_REQUEST: String =
        """
        {
          "name": "뽀또의 보드"
        }
        """

    const val RENAMED_BOARD_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            "name": "뽀또의 보드"
          },
          "error": null
        }
        """

    const val BOARD_DETAIL_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            "name": "Board 7",
            "stickers": [
              {
                "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "title": "동물 밈 짤줍",
                "isNew": false,
                "type": "IMAGE",
                "imageUrl": "https://storage.googleapis.com/ppotto-stickers/01983f2b.png?X-Goog-Expires=3600&X-Goog-Signature=sample",
                "textContent": null,
                "posX": 62.5,
                "posY": 318,
                "scale": 0.8,
                "rotation": -12,
                "zIndex": 3,
                "badgeOffsetX": -24,
                "badgeOffsetY": 96,
                "badgeRotation": 0
              },
              {
                "id": "01983f2b-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
                "title": "언제까지 일해요",
                "isNew": true,
                "type": "TEXT",
                "imageUrl": null,
                "textContent": "whats in my mac",
                "posX": 228,
                "posY": 250.5,
                "scale": 1,
                "rotation": 8.5,
                "zIndex": 5,
                "badgeOffsetX": 12,
                "badgeOffsetY": -60,
                "badgeRotation": -4
              }
            ],
            "drawings": [
              {
                "id": "01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "scope": "STICKER",
                "stickerId": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "stroke": {
                  "points": [[10.5, 22], [14.2, 25.1], [19.8, 27.4]]
                },
                "color": "#FFD400",
                "strokeWidth": 4
              },
              {
                "id": "01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
                "scope": "BOARD",
                "stickerId": null,
                "stroke": {
                  "points": [[120, 480.5], [126.4, 483.2], [133.1, 481]]
                },
                "color": "#FFFFFF",
                "strokeWidth": 2.5
              }
            ]
          },
          "error": null
        }
        """

    const val INVALID_LAYOUT: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "BOARD-001",
            "message": "편집 대상에 소유하지 않은 항목이 포함되어 있습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
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

    const val COUNT_LIMIT_EXCEEDED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "BOARD-003",
            "message": "보드는 최대 100개까지 만들 수 있습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val LAST_BOARD_CANNOT_BE_DELETED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "BOARD-004",
            "message": "마지막 보드는 삭제할 수 없습니다.",
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
            "code": "BOARD-005",
            "message": "분석이 진행 중인 보드는 삭제할 수 없습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
