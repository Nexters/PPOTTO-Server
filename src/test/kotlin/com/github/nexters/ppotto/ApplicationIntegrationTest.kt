package com.github.nexters.ppotto

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class ApplicationIntegrationTest(
    dsl: DSLContext,
) : IntegrationTest({
    test("애플리케이션 컨텍스트가 로드되고 데이터베이스에 연결된다") {
        dsl.selectOne().fetchSingle().value1() shouldBe 1
    }
})
