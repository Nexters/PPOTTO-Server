package com.github.nexters.ppotto.global.lock

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private const val LOCK_NAMESPACE = "aspect-test"
private const val ENTER = "enter-"
private const val EXIT = "exit-"

data class AdvisoryLockProbeCommand(
    val id: UserId,
)

@Service
class AdvisoryLockProbeService {
    @Transactional
    @AdvisoryLock(namespace = LOCK_NAMESPACE, key = "#userId")
    fun locked(
        userId: UserId,
        probe: () -> Unit,
    ): UUID {
        probe()
        return userId.value
    }

    @AdvisoryLock(namespace = LOCK_NAMESPACE, key = "#userId")
    fun unguarded(userId: UserId) = userId.value

    @Transactional
    @AdvisoryLock(namespace = LOCK_NAMESPACE, key = "#command.id")
    fun nested(
        command: AdvisoryLockProbeCommand,
        probe: () -> Unit,
    ): UUID {
        probe()
        return command.id.value
    }

    @Transactional
    fun selfInvoked(
        userId: UserId,
        probe: () -> Unit,
    ) = locked(userId, probe)
}

class AdvisoryLockAspectTest(
    probeService: AdvisoryLockProbeService,
    dataSource: DataSource,
) : IntegrationTest({
        Given("잠금이 걸린 메서드를 호출할 사용자가 주어졌을 때") {
            val userId = UserId(UUID.randomUUID())

            When("트랜잭션 안에서 그 메서드를 호출하면") {
                var acquiredDuringCall = true
                probeService.locked(userId) { acquiredDuringCall = dataSource.canAcquire("aspect-test:$userId") }

                Then("호출 중에는 다른 커넥션이 같은 키의 락을 잡지 못한다") {
                    acquiredDuringCall.shouldBeFalse()
                }

                Then("호출이 끝나면 같은 키의 락을 다시 잡을 수 있다") {
                    dataSource.canAcquire("aspect-test:$userId").shouldBeTrue()
                }

                Then("다른 키의 락은 호출과 무관하게 잡을 수 있다") {
                    dataSource.canAcquire("aspect-test:${UUID.randomUUID()}").shouldBeTrue()
                }
            }
        }

        Given("트랜잭션 없이 잠금만 선언한 메서드가 주어졌을 때") {
            val userId = UserId(UUID.randomUUID())

            When("그 메서드를 호출하면") {
                val exception = shouldThrow<IllegalStateException> { probeService.unguarded(userId) }

                Then("메서드 이름을 담은 오류로 즉시 실패한다") {
                    val message = checkNotNull(exception.message)

                    message shouldContain "unguarded"
                    message shouldContain "트랜잭션 안에서만"
                }

                Then("실패한 호출은 락을 남기지 않는다") {
                    dataSource.canAcquire("aspect-test:$userId").shouldBeTrue()
                }
            }
        }

        Given("값 클래스 프로퍼티를 키로 쓰는 명령이 주어졌을 때") {
            val command = AdvisoryLockProbeCommand(UserId(UUID.randomUUID()))

            When("그 메서드를 호출하면") {
                var acquiredDuringCall = true
                probeService.nested(command) { acquiredDuringCall = dataSource.canAcquire("aspect-test:${command.id}") }

                Then("중첩 프로퍼티의 uuid 문자열로 락을 잡는다") {
                    acquiredDuringCall.shouldBeFalse()
                }
            }
        }

        Given("같은 키를 쓰는 두 호출이 동시에 들어왔을 때") {
            val userId = UserId(UUID.randomUUID())
            val entries = CopyOnWriteArrayList<String>()
            val bothInside = CountDownLatch(2)

            When("두 호출을 동시에 실행하면") {
                runConcurrently(2) { index ->
                    probeService.locked(userId) {
                        entries += "$ENTER$index"
                        bothInside.countDown()
                        bothInside.await(500, TimeUnit.MILLISECONDS)
                        entries += "$EXIT$index"
                    }
                }.forEach { it.getOrThrow() }

                Then("두 호출은 겹치지 않고 차례로 실행된다") {
                    val first = entries[0].removePrefix(ENTER)
                    val second = entries[2].removePrefix(ENTER)

                    first shouldNotBe second
                    entries shouldBe listOf("$ENTER$first", "$EXIT$first", "$ENTER$second", "$EXIT$second")
                }
            }
        }

        Given("같은 빈의 다른 메서드가 잠금 메서드를 직접 호출할 때") {
            val userId = UserId(UUID.randomUUID())

            When("자기 호출로 실행하면") {
                var acquiredDuringCall = false
                probeService.selfInvoked(userId) { acquiredDuringCall = dataSource.canAcquire("aspect-test:$userId") }

                Then("프록시를 거치지 않아 락이 잡히지 않는다") {
                    acquiredDuringCall.shouldBeTrue()
                }
            }
        }
    })

private fun DataSource.canAcquire(lockKey: String): Boolean =
    connection.use { connection ->
        connection.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))").use { statement ->
            statement.setString(1, lockKey)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getBoolean(1)
            }
        }
    }
