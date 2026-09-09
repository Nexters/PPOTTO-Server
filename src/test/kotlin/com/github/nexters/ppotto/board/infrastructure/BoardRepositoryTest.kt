package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import javax.sql.DataSource

class BoardRepositoryTest(
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
    dataSource: DataSource,
) : IntegrationTest({
        Given("저장된 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val saved = boardRepository.save(user.id)

            When("저장된 아이디로 조회하면") {
                val found = boardRepository.findById(saved.id)

                Then("저장한 Board를 반환한다") {
                    found?.id shouldBe saved.id
                    found?.userId shouldBe user.id
                }
            }

            When("User 아이디로 조회하면") {
                val found = boardRepository.findByUserId(user.id)

                Then("해당 User의 Board 목록을 반환한다") {
                    found.map { it.id } shouldContainExactly listOf(saved.id)
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = boardRepository.findById(BoardId(UUID.randomUUID()))

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("사용자 단위 명령 잠금을 잡으려는 호출부에서") {
            val user = userRepository.saveTestUser()

            When("트랜잭션 없이 잠금을 요청하면") {
                Then("autocommit으로 곧바로 풀리지 않도록 거부한다") {
                    shouldThrow<IllegalStateException> {
                        boardRepository.lockCommandsByUserId(user.id)
                    }
                }
            }

            When("트랜잭션 안에서 잠금을 요청하면") {
                val takenInsideTransaction =
                    transactionTemplate.execute {
                        boardRepository.lockCommandsByUserId(user.id)
                        dataSource.tryCommandLock(user.id)
                    }

                Then("잠금이 걸려 있는 동안 다른 커넥션은 같은 잠금을 잡지 못한다") {
                    takenInsideTransaction shouldBe false
                }

                Then("트랜잭션이 끝나면 다른 커넥션이 같은 잠금을 잡을 수 있다") {
                    dataSource.tryCommandLock(user.id) shouldBe true
                }
            }
        }
    })

private fun DataSource.tryCommandLock(userId: UserId): Boolean =
    connection.use { connection ->
        connection.prepareStatement("select pg_try_advisory_xact_lock(hashtextextended(?::text, 0))").use { statement ->
            statement.setString(1, "board-user:$userId")
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "advisory 잠금 시도 결과가 비어 있습니다." }
                resultSet.getBoolean(1)
            }
        }
    }
