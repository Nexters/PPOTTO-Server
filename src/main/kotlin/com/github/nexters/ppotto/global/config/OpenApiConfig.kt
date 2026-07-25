package com.github.nexters.ppotto.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("뽀또 API")
                .version("v1")
                .description(
                    """
                    뽀또 백엔드 API 문서입니다.

                    ### 응답 형식

                    모든 응답은 공통 envelope로 내려갑니다.

                    - 성공: `{"success": true, "data": { ... }}`
                    - 실패: `{"success": false, "error": {"code": "COMMON001", "message": "잘못된 입력입니다.", "fieldErrors": []}}`

                    ### 공통 에러 코드

                    | 코드 | 상태 | 설명 |
                    |---|---|---|
                    | COMMON000 | 500 | 서버 오류 |
                    | COMMON001 | 400 | 잘못된 입력 |
                    | COMMON002 | 404 | 리소스 없음 |
                    | COMMON003 | 405 | 허용되지 않은 메서드 |
                    | COMMON004 | 401 | 인증 필요 |
                    | COMMON005 | 403 | 권한 없음 |
                    | COMMON006 | 409 | 충돌 |
                    """.trimIndent(),
                ).contact(
                    Contact()
                        .name("Github Repository")
                        .url("https://github.com/Nexters/Gallery100-Server"),
                ),
        )
}
