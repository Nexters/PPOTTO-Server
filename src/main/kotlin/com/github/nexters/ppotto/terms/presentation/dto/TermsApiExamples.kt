package com.github.nexters.ppotto.terms.presentation.dto

object TermsApiExamples {
    const val CURRENT_TERMS_RESPONSE: String =
        """
        {
          "success": true,
          "data": [
            {
              "id": "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
              "code": "TOS",
              "version": "1.0",
              "isRequired": true,
              "contentUrl": "https://nexters.notion.site/ppotto-tos",
              "agreed": true
            },
            {
              "id": "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
              "code": "PRIVACY",
              "version": "1.1",
              "isRequired": true,
              "contentUrl": "https://nexters.notion.site/ppotto-privacy",
              "agreed": false
            }
          ],
          "error": null
        }
        """

    const val ANONYMOUS_TERMS_RESPONSE: String =
        """
        {
          "success": true,
          "data": [
            {
              "id": "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
              "code": "TOS",
              "version": "1.0",
              "isRequired": true,
              "contentUrl": "https://nexters.notion.site/ppotto-tos",
              "agreed": false
            }
          ],
          "error": null
        }
        """

    const val AGREE_TERMS_REQUEST: String =
        """
        {
          "termIds": [
            "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a"
          ]
        }
        """

    const val REQUIRED_TERMS_MISSING: String =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "TERM-001",
            "message": "필수 약관에 동의해야 합니다.",
            "fieldErrors": [],
            "timestamp": "2026-07-27T05:02:11Z"
          }
        }
        """
}
