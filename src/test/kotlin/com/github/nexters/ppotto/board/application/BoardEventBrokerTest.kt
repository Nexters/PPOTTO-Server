package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.UUID

class BoardEventBrokerTest :
    FunSpec({
        test("이벤트는 같은 보드의 활성 연결에만 전달한다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val otherBoardId = boardId()
            val connection = RecordingBoardEventConnection()
            val otherConnection = RecordingBoardEventConnection()
            val subscription = broker.subscribe(boardId, userId(), connection)
            val otherSubscription = broker.subscribe(otherBoardId, userId(), otherConnection)
            try {
                val event = BoardEvent.updated(boardId)
                broker.publish(event)

                connection.events shouldContainExactly listOf(event)
                otherConnection.events.shouldBeEmpty()
            } finally {
                subscription.close()
                otherSubscription.close()
            }
        }

        test("종료한 연결에는 이벤트를 전달하지 않는다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val connection = RecordingBoardEventConnection()
            val subscription = broker.subscribe(boardId, userId(), connection)

            subscription.close()
            broker.publish(BoardEvent.updated(boardId))

            connection.events.shouldBeEmpty()
        }

        test("나간 사용자의 연결만 종료한다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val departingUserId = userId()
            val departingConnection = RecordingBoardEventConnection()
            val secondDepartingConnection = RecordingBoardEventConnection()
            val remainingConnection = RecordingBoardEventConnection()
            broker.subscribe(boardId, departingUserId, departingConnection)
            broker.subscribe(boardId, departingUserId, secondDepartingConnection)
            val remainingSubscription = broker.subscribe(boardId, userId(), remainingConnection)
            try {
                broker.disconnect(boardId, departingUserId)
                val event = BoardEvent.participantsUpdated(boardId)
                broker.publish(event)

                departingConnection.completeCount shouldBe 1
                departingConnection.events.shouldBeEmpty()
                secondDepartingConnection.completeCount shouldBe 1
                secondDepartingConnection.events.shouldBeEmpty()
                remainingConnection.events shouldContainExactly listOf(event)
            } finally {
                remainingSubscription.close()
            }
        }

        test("보드 삭제 이벤트를 모든 구독자에게 보내고 연결을 종료한다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val firstConnection = RecordingBoardEventConnection()
            val secondConnection = RecordingBoardEventConnection()
            broker.subscribe(boardId, userId(), firstConnection)
            broker.subscribe(boardId, userId(), secondConnection)

            val event = BoardEvent.deleted(boardId)
            broker.publishAndClose(event)
            broker.publish(BoardEvent.updated(boardId))

            firstConnection.events shouldContainExactly listOf(event)
            secondConnection.events shouldContainExactly listOf(event)
            firstConnection.completeCount shouldBe 1
            secondConnection.completeCount shouldBe 1
        }

        test("이벤트 전송에 실패한 연결은 다음 전송 대상에서 제거한다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val connection = RecordingBoardEventConnection(sendResult = false)
            broker.subscribe(boardId, userId(), connection)

            broker.publish(BoardEvent.updated(boardId))
            broker.publish(BoardEvent.updated(boardId))

            connection.sendCount shouldBe 1
            connection.completeCount shouldBe 1
        }

        test("heartbeat에 실패한 연결은 다음 heartbeat 대상에서 제거한다") {
            val broker = BoardEventBroker()
            val connection = RecordingBoardEventConnection(heartbeatResult = false)
            broker.subscribe(boardId(), userId(), connection)

            broker.heartbeat()
            broker.heartbeat()

            connection.heartbeatCount shouldBe 1
            connection.completeCount shouldBe 1
        }
    })

private class RecordingBoardEventConnection(
    private val sendResult: Boolean = true,
    private val heartbeatResult: Boolean = true,
) : BoardEventConnection {
    val events = mutableListOf<BoardEvent>()
    var sendCount = 0
    var heartbeatCount = 0
    var completeCount = 0

    override fun send(event: BoardEvent): Boolean {
        sendCount += 1
        if (sendResult) events += event
        return sendResult
    }

    override fun heartbeat(): Boolean {
        heartbeatCount += 1
        return heartbeatResult
    }

    override fun complete() {
        completeCount += 1
    }
}

private fun boardId(): BoardId = BoardId(UUID.randomUUID())

private fun userId(): UserId = UserId(UUID.randomUUID())
