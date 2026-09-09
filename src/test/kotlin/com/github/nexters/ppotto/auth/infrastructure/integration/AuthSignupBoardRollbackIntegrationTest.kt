package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

@Import(FailingBoardAuthTestConfig::class)
class AuthSignupBoardRollbackIntegrationTest(
    authUserPort: AuthUserPort,
    userRepository: UserRepository,
    @Qualifier(SIGNUP_TRANSACTION) signupTransaction: TransactionOperations,
) : IntegrationTest({
        Given("신규 가입 중 기본 보드 생성이 실패할 때") {
            val providerUserId = "board-rollback-${UUID.randomUUID()}"

            When("가입 트랜잭션 안에서 가입 port를 호출하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        signupTransaction.execute {
                            authUserPort.findOrCreate(
                                SocialProfile(
                                    provider = OAuthProvider.KAKAO,
                                    providerUserId = providerUserId,
                                    email = "board-rollback@example.com",
                                    name = "보드롤백사용자",
                                ),
                            )
                        }
                    }

                Then("사용자 생성까지 함께 롤백해 유령 계정을 남기지 않는다") {
                    exception.message shouldBe "기본 보드 생성 실패"
                    userRepository
                        .findBySocialAccount(OAuthProvider.KAKAO, providerUserId)
                        .shouldBeNull()
                }
            }
        }
    })

@TestConfiguration(proxyBeanMethods = false)
class FailingBoardAuthTestConfig {
    @Bean
    @Primary
    fun failingBoardCommandService(
        boardRepository: BoardRepository,
        boardAccessService: BoardAccessService,
        drawingRepository: DrawingRepository,
        analysisActivityPort: BoardAnalysisActivityPort,
        stickerCommandPort: BoardStickerCommandPort,
    ): BoardCommandService =
        FailingBoardCommandService(
            boardRepository,
            boardAccessService,
            drawingRepository,
            analysisActivityPort,
            stickerCommandPort,
        )
}

open class FailingBoardCommandService(
    boardRepository: BoardRepository,
    boardAccessService: BoardAccessService,
    drawingRepository: DrawingRepository,
    analysisActivityPort: BoardAnalysisActivityPort,
    stickerCommandPort: BoardStickerCommandPort,
) : BoardCommandService(boardRepository, boardAccessService, drawingRepository, analysisActivityPort, stickerCommandPort) {
    override fun createDefault(userId: UserId): Board = error("기본 보드 생성 실패")
}
