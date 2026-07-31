package com.github.nexters.ppotto.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest(
    body: BehaviorSpec.() -> Unit = {},
) : BehaviorSpec({
        beforeSpec { DatabaseCleaner.clear() }
        body()
    }) {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
}
