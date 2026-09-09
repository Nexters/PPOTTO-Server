package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.global.lock.ADVISORY_LOCK_ADVICE_ORDER
import com.github.nexters.ppotto.global.lock.AdvisoryLockAspect
import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor

class TransactionAdviceOrderTest(
    context: ApplicationContext,
) : IntegrationTest({
        Given("애플리케이션 컨텍스트가 떠 있을 때") {
            When("트랜잭션 어드바이저를 찾으면") {
                val advisors = context.getBeansOfType(BeanFactoryTransactionAttributeSourceAdvisor::class.java).values

                Then("트랜잭션 어드바이저는 하나뿐이다") {
                    advisors.size shouldBe 1
                }

                Then("트랜잭션 어드바이스 순서는 TransactionConfig가 지정한 값이다") {
                    advisors.single().order shouldBe TRANSACTION_ADVICE_ORDER
                }

                Then("부트 기본값인 최저 우선순위가 아니다") {
                    advisors.single().order shouldNotBe Ordered.LOWEST_PRECEDENCE
                }
            }

            When("락 애스펙트를 찾으면") {
                val aspect = context.getBeansOfType(AdvisoryLockAspect::class.java).values

                Then("락 애스펙트는 하나의 빈으로 등록되어 있다") {
                    aspect.size shouldBe 1
                }

                Then("락 애스펙트 순서는 ADVISORY_LOCK_ADVICE_ORDER다") {
                    OrderUtils.getOrder(AdvisoryLockAspect::class.java) shouldBe ADVISORY_LOCK_ADVICE_ORDER
                }

                Then("락 애스펙트는 트랜잭션 어드바이스보다 안쪽에서 돈다") {
                    TRANSACTION_ADVICE_ORDER shouldBeLessThan ADVISORY_LOCK_ADVICE_ORDER
                }
            }
        }
    })
