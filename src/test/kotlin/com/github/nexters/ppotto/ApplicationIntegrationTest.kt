package com.github.nexters.ppotto

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class ApplicationIntegrationTest(
    dsl: DSLContext,
) : IntegrationTest({
        Given("애플리케이션이 기동된 상태에서") {
            When("데이터베이스에 쿼리를 실행하면") {
                Then("정상적으로 응답한다") {
                    dsl.selectOne().fetchSingle().value1() shouldBe 1
                }
            }
        }
    })
