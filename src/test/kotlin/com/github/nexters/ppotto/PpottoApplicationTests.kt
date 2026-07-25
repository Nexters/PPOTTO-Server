package com.github.nexters.ppotto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PpottoApplicationTests(
    private val applicationContext: ApplicationContext,
) : FunSpec({
    test("애플리케이션 컨텍스트가 로드된다") {
        applicationContext.shouldNotBeNull()
    }
})
