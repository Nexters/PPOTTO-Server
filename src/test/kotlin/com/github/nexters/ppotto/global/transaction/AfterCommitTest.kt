package com.github.nexters.ppotto.global.transaction

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CopyOnWriteArrayList

private const val IN_TRANSACTION = "트랜잭션 안"
private const val AFTER_COMMIT = "커밋 뒤"

class AfterCommitTest(
    transactionTemplate: TransactionTemplate,
) : IntegrationTest({
        Given("트랜잭션 안에서 후처리를 등록했을 때") {
            When("그 트랜잭션이 커밋되면") {
                val events = CopyOnWriteArrayList<String>()
                transactionTemplate.executeWithoutResult {
                    afterCommit { events += AFTER_COMMIT }
                    events += IN_TRANSACTION
                }

                Then("블록은 트랜잭션 안에서 실행되지 않는다") {
                    events.first() shouldBe IN_TRANSACTION
                }

                Then("커밋 뒤에 한 번만 실행된다") {
                    events shouldBe listOf(IN_TRANSACTION, AFTER_COMMIT)
                }
            }

            When("그 트랜잭션이 롤백되면") {
                val events = CopyOnWriteArrayList<String>()
                transactionTemplate.executeWithoutResult { status ->
                    afterCommit { events += AFTER_COMMIT }
                    status.setRollbackOnly()
                }

                Then("블록은 실행되지 않는다") {
                    events.shouldBeEmpty()
                }
            }
        }

        Given("트랜잭션 동기화가 없을 때") {
            When("후처리를 등록하면") {
                val events = CopyOnWriteArrayList<String>()
                afterCommit { events += AFTER_COMMIT }

                Then("등록 즉시 실행된다") {
                    events shouldBe listOf(AFTER_COMMIT)
                }
            }
        }
    })
