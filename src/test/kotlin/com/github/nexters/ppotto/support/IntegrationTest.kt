package com.github.nexters.ppotto.support

import io.kotest.core.spec.style.FunSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest(body: FunSpec.() -> Unit = {}) : FunSpec(body)
