package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.identifier.BoardId
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
            val subscription = broker.subscribe(boardId, connection)
            val otherSubscription = broker.subscribe(otherBoardId, otherConnection)
            try {
                val event = BoardEvent.layoutUpdated(boardId)
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
            val subscription = broker.subscribe(boardId, connection)

            subscription.close()
            broker.publish(BoardEvent.layoutUpdated(boardId))

            connection.events.shouldBeEmpty()
        }

        test("이벤트 전송에 실패한 연결은 다음 전송 대상에서 제거한다") {
            val broker = BoardEventBroker()
            val boardId = boardId()
            val connection = RecordingBoardEventConnection(sendResult = false)
            broker.subscribe(boardId, connection)

            broker.publish(BoardEvent.layoutUpdated(boardId))
            broker.publish(BoardEvent.layoutUpdated(boardId))

            connection.sendCount shouldBe 1
            connection.completeCount shouldBe 1
        }

        test("heartbeat에 실패한 연결은 다음 heartbeat 대상에서 제거한다") {
            val broker = BoardEventBroker()
            val connection = RecordingBoardEventConnection(heartbeatResult = false)
            broker.subscribe(boardId(), connection)

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
