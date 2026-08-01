package com.github.nexters.ppotto.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.testContextManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, ObjectStorageTestConfiguration::class)
abstract class IntegrationTest(
    body: BehaviorSpec.() -> Unit = {},
) : BehaviorSpec({
        beforeSpec {
            testContextManager()
                .testContext
                .applicationContext
                .getBean(DatabaseCleaner::class.java)
                .clear()
        }
        body()
    }) {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
}
