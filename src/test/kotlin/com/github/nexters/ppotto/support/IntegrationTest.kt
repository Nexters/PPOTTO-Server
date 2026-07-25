package com.github.nexters.ppotto.support

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest(
    body: BehaviorSpec.() -> Unit = {},
) : BehaviorSpec(body)
