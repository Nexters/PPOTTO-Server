package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class BoardParticipantQueryServiceTest(
    queryService: BoardParticipantQueryService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 두 참여자가 있는 보드에서") {
            val owner = userRepository.saveTestUser("김용희")
            val firstMember = userRepository.saveTestUser("이수민")
            val secondMember = userRepository.saveTestUser("박지훈")
            val stranger = userRepository.saveTestUser("최민지")
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, firstMember.id)
            dslContext.addBoardMember(board.id, secondMember.id)

            When("방장이 참여자 목록을 조회하면") {
                val result = queryService.get(board.id, owner.id)

                Then("방장 우선, 이후 참여 순서와 인원 제한을 반환한다") {
                    result.participantCount shouldBe 3
                    result.maxParticipantCount shouldBe 8
                    result.participants.map { Triple(it.userId, it.name, it.role) } shouldBe
                        listOf(
                            Triple(owner.id, "김용희", BoardRole.OWNER),
                            Triple(firstMember.id, "이수민", BoardRole.MEMBER),
                            Triple(secondMember.id, "박지훈", BoardRole.MEMBER),
                        )
                }
            }

            When("참여자가 참여자 목록을 조회하면") {
                val result = queryService.get(board.id, firstMember.id)

                Then("동일한 참여자 목록을 반환한다") {
                    result.participants.map { it.userId } shouldBe
                        listOf(owner.id, firstMember.id, secondMember.id)
                }
            }

            When("보드와 무관한 사용자가 조회하면") {
                Then("보드 존재를 노출하지 않는다") {
                    shouldThrow<NotFoundException> { queryService.get(board.id, stranger.id) }
                        .errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })
