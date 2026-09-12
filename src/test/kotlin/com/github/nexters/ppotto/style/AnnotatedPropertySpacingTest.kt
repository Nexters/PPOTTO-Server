package com.github.nexters.ppotto.style

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

private val SOURCE_ROOTS = listOf("src/main/kotlin", "src/test/kotlin")

private val ANNOTATION_ONLY = Regex("""^@[\w:.]+(\(.*\)|\()?$""")
private val PROPERTY_DECLARATION = Regex("""^(private |internal |public |protected )?(override )?(val|var) .*""")

class AnnotatedPropertySpacingTest :
    BehaviorSpec({
        Given("코틀린 소스 전체에서") {
            When("어노테이션이 붙은 프로퍼티 앞을 확인하면") {
                val violations = SOURCE_ROOTS.flatMap { violationsIn(it) }

                Then("앞 프로퍼티와 빈 줄로 분리되어 있다") {
                    violations.shouldBeEmpty()
                }
            }
        }
    })

private fun violationsIn(root: String): List<String> =
    File(root)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            val lines = file.readText().lines()
            lines.asSequence().withIndex().mapNotNull { (index, line) ->
                val previous = lines.getOrNull(index - 1)?.trim() ?: return@mapNotNull null
                val current = line.trim()
                val startsGroup =
                    ANNOTATION_ONLY.matches(current) &&
                        previous.isNotEmpty() &&
                        !previous.startsWith("@") &&
                        !previous.endsWith("(") &&
                        (previous.endsWith(",") || PROPERTY_DECLARATION.matches(previous))
                if (startsGroup) "${file.path}:${index + 1} $current" else null
            }
        }.toList()
