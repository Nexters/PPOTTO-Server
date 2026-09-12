package com.github.nexters.ppotto.analysis.infrastructure.gemini

private const val RESOURCE_ROOT = "prompts"
private val PLACEHOLDER = Regex("""\{\{(\w+)}}""")

internal fun promptSection(
    path: String,
    vararg values: Pair<String, Any?>,
): PromptSection {
    val template = readTemplate(path)
    val supplied = values.toMap()
    val used = mutableSetOf<String>()
    val rendered =
        PLACEHOLDER.replace(template) { match ->
            val key = match.groupValues[1]
            require(supplied.containsKey(key)) { "$path 의 {{$key}} 자리에 넣을 값이 없습니다." }
            used += key
            supplied.getValue(key).toString()
        }
    val unused = supplied.keys - used
    require(unused.isEmpty()) { "$path 에 쓰이지 않는 값을 넘겼습니다: $unused" }

    return PromptSection(sectionNameOf(path), rendered)
}

private fun readTemplate(path: String): String {
    val resource = "$RESOURCE_ROOT/$path.md"
    val text =
        checkNotNull(
            PromptSection::class.java.classLoader
                .getResourceAsStream(resource),
        ) {
            "프롬프트 리소스를 찾을 수 없습니다: $resource"
        }.use { it.readBytes().decodeToString() }
    require(text.isNotBlank()) { "프롬프트 리소스가 비어 있습니다: $resource" }
    return text.trimEnd('\n')
}

private fun sectionNameOf(path: String): String = path.substringAfterLast('/').replace('-', ' ')
