package com.github.nexters.ppotto.image.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.image.application.ImageUploadService
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.image.support.ImageUploadTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
@Import(ImageUploadTestConfig::class)
class ImageUploadControllerTest(
    @Autowired val mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    imageUploadService: ImageUploadService,
) : IntegrationTest({
        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("파일명 배열을 담아 signed URL 발급을 요청하면") {
                Then("성공 응답에 uploadSessionId와 이미지별 signed URL이 담긴다") {
                    mockMvc
                        .perform(
                            post("/api/v1/boards/${board.id}/images/signed-urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"fileNames":["IMG_0001.jpg","IMG_0002.png"]}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.uploadSessionId").exists())
                        .andExpect(jsonPath("$.data.images.length()").value(2))
                        .andExpect(jsonPath("$.data.images[0].imageId").exists())
                        .andExpect(jsonPath("$.data.images[0].fileName").exists())
                        .andExpect(jsonPath("$.data.images[0].uploadUrl").exists())
                }
            }

            When("빈 파일명 배열로 요청하면") {
                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/api/v1/boards/${board.id}/images/signed-urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"fileNames":[]}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("200개를 초과하는 파일명 배열로 요청하면") {
                val tooManyFileNames = (1..201).map { "IMG_$it.jpg" }
                val body = tooManyFileNames.joinToString(",", "{\"fileNames\":[", "]}") { "\"$it\"" }

                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/api/v1/boards/${board.id}/images/signed-urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("발급받은 Image에 대해 업로드 완료를 보고하면") {
                val imageId =
                    imageUploadService
                        .issueUploadUrls(board.id, listOf("IMG_0001.jpg"))
                        .items
                        .single()
                        .imageId

                Then("uploadStatus가 COMPLETED로 바뀐 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/api/v1/boards/${board.id}/images/$imageId/upload-status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"result":"COMPLETED"}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.imageId").value(imageId.toString()))
                        .andExpect(jsonPath("$.data.uploadStatus").value("COMPLETED"))
                }
            }

            When("이미 완료 처리된 Image에 대해 다시 완료를 보고하면") {
                val imageId =
                    imageUploadService
                        .issueUploadUrls(board.id, listOf("IMG_0001.jpg"))
                        .items
                        .single()
                        .imageId
                imageUploadService.completeUpload(board.id, imageId, UploadStatus.COMPLETED)

                Then("409 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/api/v1/boards/${board.id}/images/$imageId/upload-status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"result":"FAILED"}"""),
                        ).andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        Given("존재하지 않는 imageId로") {
            val board = boardRepository.save(userRepository.save().id)

            When("업로드 완료를 보고하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/api/v1/boards/${board.id}/images/${UUID.randomUUID()}/upload-status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"result":"COMPLETED"}"""),
                        ).andExpect(status().isNotFound)
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("signed URL 발급을 요청하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/api/v1/boards/${java.util.UUID.randomUUID()}/images/signed-urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"fileNames":["IMG_0001.jpg"]}"""),
                        ).andExpect(status().isNotFound)
                }
            }
        }
    })
