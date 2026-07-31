package com.github.nexters.ppotto.board

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File

private const val SOURCE_ROOT = "src/main/kotlin/com/github/nexters/ppotto"

class BoardAnalysisDependencyTest :
    BehaviorSpec({
        Given("board 도메인 소스 트리에서") {
            Then("소스 루트를 실제로 찾는다") {
                File("$SOURCE_ROOT/board").isDirectory shouldBe true
            }

            When("analysis 도메인 참조를 모으면") {
                val analysisReferences = importsOf("$SOURCE_ROOT/board").startingWith("com.github.nexters.ppotto.analysis")

                Then("AnalysisRepository를 포함한 analysis 타입을 직접 import 하지 않는다") {
                    analysisReferences.shouldBeEmpty()
                }
            }
        }

        Given("analysis 도메인 소스 트리에서") {
            When("board 도메인 참조를 모으면") {
                val boardReferences = importsOf("$SOURCE_ROOT/analysis").startingWith("com.github.nexters.ppotto.board")

                Then("board repository를 직접 import 하지 않는다") {
                    boardReferences.startingWith("com.github.nexters.ppotto.board.infrastructure").shouldBeEmpty()
                }

                Then("application 경계와 port 계약만 참조한다") {
                    boardReferences
                        .map { it.second }
                        .shouldContainExactlyInAnyOrder(
                            "com.github.nexters.ppotto.board.application.BoardAccessService",
                            "com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort",
                        )
                }
            }
        }
    })

private fun importsOf(directory: String): List<Pair<String, String>> =
    File(directory)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            file
                .readLines()
                .filter { it.startsWith("import ") }
                .map { file.name to it.removePrefix("import ").trim() }
        }.toList()

private fun List<Pair<String, String>>.startingWith(prefix: String): List<Pair<String, String>> =
    filter { it.second.startsWith("$prefix.") }
