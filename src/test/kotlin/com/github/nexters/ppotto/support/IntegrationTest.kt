package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.notification.support.NotificationTestConfig
import com.github.nexters.ppotto.user.support.UserTestConfig
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.testContextManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(
    TestcontainersConfiguration::class,
    ObjectStorageTestConfiguration::class,
    AnalysisTestConfig::class,
    NotificationTestConfig::class,
    UserTestConfig::class,
)
abstract class IntegrationTest(
    body: BehaviorSpec.() -> Unit = {},
) : BehaviorSpec({
        beforeTest { testCase ->
            if (testCase.parent == null) {
                val applicationContext = testContextManager().testContext.applicationContext
                applicationContext.getBean(DatabaseCleaner::class.java).clear()
                applicationContext
                    .getBeansOfType(ResettableFake::class.java)
                    .values
                    .forEach { it.reset() }
            }
        }
        body()
    }) {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
}
