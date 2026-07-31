package com.github.nexters.ppotto.user.presentation.dto

object UserApiExamples {
    const val KAKAO_USER_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2a-7c31-7b02-93d4-1f2e3d4c5b6a",
            "provider": "KAKAO",
            "email": "ppotto@kakao.com",
            "createdAt": "2026-07-01T09:12:33+09:00"
          },
          "error": null
        }
        """

    const val APPLE_PRIVATE_RELAY_USER_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "id": "01983f2a-6b20-7a01-82c3-0e1d2c3b4a59",
            "provider": "APPLE",
            "email": "mxq7r2v9td@privaterelay.appleid.com",
            "createdAt": "2026-07-15T21:40:05+09:00"
          },
          "error": null
        }
        """
}
