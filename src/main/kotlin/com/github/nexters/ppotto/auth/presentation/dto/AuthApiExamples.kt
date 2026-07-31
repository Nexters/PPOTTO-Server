package com.github.nexters.ppotto.auth.presentation.dto

object AuthApiExamples {
    const val KAKAO_LOGIN_REQUEST: String =
        """
        {
          "provider": "KAKAO",
          "accessToken": "v1.sample-kakao-oauth-access-token"
        }
        """

    const val APPLE_FIRST_LOGIN_REQUEST: String =
        """
        {
          "provider": "APPLE",
          "identityToken": "eyJhbGciOiJSUzI1NiJ9.sample-apple-identity-token.sample-signature",
          "authorizationCode": "sample.0.srtwx.apple-authorization-code",
          "rawNonce": "4A7F0E2B-9C31-45D8-A6F2-8B0C3D9E1F52"
        }
        """

    const val APPLE_RELOGIN_REQUEST: String =
        """
        {
          "provider": "APPLE",
          "identityToken": "eyJhbGciOiJSUzI1NiJ9.sample-apple-identity-token.sample-signature",
          "authorizationCode": "sample.0.mnpqr.apple-authorization-code",
          "rawNonce": "0F9E8D7C-6B5A-4938-A716-05F4E3D2C1B0"
        }
        """

    const val NEW_USER_LOGIN_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "accessToken": "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature",
            "refreshToken": "sample-refresh-token-01983f2a7c317b02",
            "accessTokenExpiresIn": 3600,
            "isNewUser": true,
            "pendingTerms": [
              {
                "id": "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
                "code": "TOS",
                "version": "1.0",
                "isRequired": true,
                "contentUrl": "https://nexters.notion.site/ppotto-tos",
                "agreed": false
              },
              {
                "id": "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
                "code": "PRIVACY",
                "version": "1.0",
                "isRequired": true,
                "contentUrl": "https://nexters.notion.site/ppotto-privacy",
                "agreed": false
              }
            ]
          },
          "error": null
        }
        """

    const val RETURNING_USER_LOGIN_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "accessToken": "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature",
            "refreshToken": "sample-refresh-token-01983f2a6b207a01",
            "accessTokenExpiresIn": 3600,
            "isNewUser": false,
            "pendingTerms": []
          },
          "error": null
        }
        """

    const val TOKEN_PAIR_RESPONSE: String =
        """
        {
          "success": true,
          "data": {
            "accessToken": "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature",
            "refreshToken": "sample-refresh-token-01983f2a4d5e7f6a",
            "accessTokenExpiresIn": 3600
          },
          "error": null
        }
        """

    const val SOCIAL_AUTHENTICATION_FAILED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "AUTH-001",
            "message": "소셜 로그인 검증에 실패했습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val APPLE_CODE_EXCHANGE_FAILED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "AUTH-003",
            "message": "애플 인증 코드 교환에 실패했습니다. 다시 로그인해 주세요.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val KAKAO_EMAIL_CONSENT_REQUIRED: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "AUTH-004",
            "message": "이메일 제공에 동의해야 가입할 수 있습니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """

    const val INVALID_REFRESH_TOKEN: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "AUTH-002",
            "message": "로그인이 만료되었습니다. 다시 로그인해 주세요.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
