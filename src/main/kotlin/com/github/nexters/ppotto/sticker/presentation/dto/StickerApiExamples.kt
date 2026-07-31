package com.github.nexters.ppotto.sticker.presentation.dto

object StickerApiExamples {
    const val RECAP_DETAIL_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "sticker": {
              "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
              "title": "동물 밈 짤줍",
              "isNew": false,
              "type": "IMAGE",
              "imageUrl": "https://storage.googleapis.com/ppotto-stickers/01983f2b.png?X-Goog-Signature=sample",
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
            "comments": [
              {
                "id": "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "content": "웃기고 귀여우면 일단 주워요",
                "isFloat": true,
                "posX": 0,
                "posY": -140
              },
              {
                "id": "01983f2d-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
                "content": "또 고양이가 주워왔네요",
                "isFloat": false,
                "posX": null,
                "posY": null
              }
            ],
            "photos": [
              {
                "id": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Signature=sample",
                "takenAt": "2026-06-14T13:22:10+09:00"
              },
              {
                "id": "01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
                "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2f.jpg?X-Goog-Signature=sample",
                "takenAt": "2026-07-02T19:05:44+09:00"
              }
            ]
          },
          "error": null
        }
        """

    const val UPDATE_STICKER_TITLE_REQUEST: String =
        """
        {
          "title": "고양이 모음집"
        }
        """

    const val UPDATE_STICKER_TITLE_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "title": "고양이 모음집"
          },
          "error": null
        }
        """

    const val STICKER_NOT_FOUND: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "STICKER-001",
            "message": "스티커를 찾을 수 없습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
