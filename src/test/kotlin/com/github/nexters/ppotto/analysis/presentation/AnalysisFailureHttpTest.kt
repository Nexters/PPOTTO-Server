package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.model.CreateAnalysisCommand
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.analysis.support.FakeStickerGenerator
import com.github.nexters.ppotto.analysis.support.photoUploadGroups
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.annotation.DirtiesContext
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnalysisFailureHttpTest(
    environment: Environment,
    objectMapper: ObjectMapper,
    tokenProvider: TokenProvider,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
    analysisService: AnalysisService,
    photoStorage: FakePhotoStorage,
    stickerGenerator: FakeStickerGenerator,
) : IntegrationTest({
        val client = HttpClient.newHttpClient()

        afterSpec { client.close() }

        fun request(
            path: String,
            accessToken: String,
            method: String = "GET",
        ): HttpResponse<String> =
            client.send(
                HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:${environment.getRequiredProperty("local.server.port")}$path"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer $accessToken")
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        Given("실패 코드가 저장된 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            analysisRepository.markFailed(analysis.id, "사진 분석에 실패했습니다.", AnalysisErrorCode.CLASSIFICATION_FAILED)
            val accessToken = tokenProvider.issue(board.userId).accessToken

            When("실제 HTTP 연결로 분석 상태를 조회하면") {
                val response = request("/analysis/${analysis.id}", accessToken)

                Then("HTTP 200 성공 봉투에 FAILED와 코드 문자열을 반환한다") {
                    response.statusCode() shouldBe 200
                    val body = objectMapper.readTree(response.body())
                    body.path("success").asBoolean() shouldBe true
                    body
                        .path("data")
                        .path("status")
                        .asText() shouldBe "FAILED"
                    body
                        .path("data")
                        .path("failedCode")
                        .asText() shouldBe "ANALYSIS-017"
                    body.has("error") shouldBe false
                }
            }
        }

        Given("업로드된 사진의 스티커 생성이 모두 실패할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups(20)))
            photoStorage.markUploaded(photoRepository.findPendingByAnalysisId(created.analysisId))
            stickerGenerator.onGenerate = { error("테스트 스티커 배경 제거 실패") }
            val accessToken = tokenProvider.issue(board.userId).accessToken

            When("실제 HTTP로 분석을 시작한 뒤 상태를 조회하면") {
                val started = request("/analysis/${created.analysisId}/start", accessToken, "POST")
                val polled = request("/analysis/${created.analysisId}", accessToken)

                Then("시작 수락 뒤 전체 생성 실패를 ANALYSIS-013로 반환한다") {
                    started.statusCode() shouldBe 202
                    polled.statusCode() shouldBe 200
                    val body = objectMapper.readTree(polled.body())
                    body.path("success").asBoolean() shouldBe true
                    body
                        .path("data")
                        .path("status")
                        .asText() shouldBe "FAILED"
                    body
                        .path("data")
                        .path("failedCode")
                        .asText() shouldBe "ANALYSIS-013"
                    body.has("error") shouldBe false
                }
            }
        }
    })
