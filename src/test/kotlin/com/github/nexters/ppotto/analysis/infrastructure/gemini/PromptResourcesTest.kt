package com.github.nexters.ppotto.analysis.infrastructure.gemini

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class PromptResourcesTest :
    BehaviorSpec({
        val aliases = (1..100).map { "P%03d".format(it) }

        Given("모든 프롬프트를 렌더링했을 때") {
            val prompts =
                listOf(
                    themeClassificationPrompt(aliases),
                    stickerRegenerationPrompt(aliases, "P002"),
                    stickerSubjectVerificationPrompt("빨간 우산"),
                )

            When("렌더 결과를 확인하면") {
                Then("치환되지 않은 자리가 남지 않는다") {
                    prompts.forEach { it.text shouldNotContain "{{" }
                    COPY_VOICE shouldNotContain "{{"
                }

                Then("리소스 파일 이름이 섹션 이름이 된다") {
                    themeClassificationPrompt(aliases).sectionNames shouldBe
                        listOf("task", "photo aliases", "field order", "sticker candidate guide", "copy style examples", "output language")
                }
            }
        }

        Given("존재하지 않는 프롬프트 리소스를 가리켰을 때") {
            When("섹션을 만들면") {
                val failure = shouldThrow<IllegalStateException> { promptSection("shared/does-not-exist") }

                Then("빈 지시를 모델에 보내지 않고 경로와 함께 실패한다") {
                    failure.message!! shouldContain "prompts/shared/does-not-exist.md"
                }
            }
        }

        Given("치환할 값을 빠뜨렸을 때") {
            When("섹션을 만들면") {
                val failure = shouldThrow<IllegalArgumentException> { promptSection("theme-classification/task") }

                Then("어느 자리가 비었는지 알린다") {
                    failure.message!! shouldContain "minThemeCount"
                }
            }
        }

        Given("쓰이지 않는 값을 넘겼을 때") {
            When("섹션을 만들면") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        promptSection("shared/output-language", "typo" to "값")
                    }

                Then("오타가 조용히 무시되지 않고 실패한다") {
                    failure.message!! shouldContain "typo"
                }
            }
        }
    })
