package com.github.nexters.ppotto.board

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.io.File

private const val SOURCE_ROOT = "src/main/kotlin/com/github/nexters/ppotto"

private val QUALIFIED_REFERENCE = Regex("""com\.github\.nexters\.ppotto(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")

class BoardAnalysisDependencyTest :
    BehaviorSpec({
        Given("board 도메인 소스 트리에서") {
            When("analysis 도메인 참조를 모으면") {
                val analysisReferences =
                    referencesOf("$SOURCE_ROOT/board").startingWith("com.github.nexters.ppotto.analysis")

                Then("import 든 인라인 FQN 이든 analysis 타입을 직접 참조하지 않는다") {
                    analysisReferences.shouldBeEmpty()
                }
            }
        }

        Given("analysis 도메인 소스 트리에서") {
            When("board 도메인 참조를 모으면") {
                val boardReferences =
                    referencesOf("$SOURCE_ROOT/analysis").startingWith("com.github.nexters.ppotto.board")

                Then("board repository를 직접 참조하지 않는다") {
                    boardReferences.startingWith("com.github.nexters.ppotto.board.infrastructure").shouldBeEmpty()
                }

                Then("application 경계와 port 계약만 참조한다") {
                    boardReferences
                        .map { it.second }
                        .distinct()
                        .shouldContainExactlyInAnyOrder(
                            "com.github.nexters.ppotto.board.application.BoardAccessService",
                            "com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort",
                        )
                }
            }
        }
    })

private fun referencesOf(directory: String): List<Pair<String, String>> =
    File(directory)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            QUALIFIED_REFERENCE
                .findAll(file.readText())
                .map { file.name to it.value }
        }.toList()

private fun List<Pair<String, String>>.startingWith(prefix: String): List<Pair<String, String>> =
    filter { it.second.startsWith("$prefix.") }
