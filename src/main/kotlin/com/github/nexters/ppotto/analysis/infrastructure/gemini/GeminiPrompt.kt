package com.github.nexters.ppotto.analysis.infrastructure.gemini

private const val SECTION_SEPARATOR = "\n\n"

internal data class PromptSection(
    val name: String,
    val text: String,
)

internal class GeminiPrompt(
    val systemInstruction: String?,
    val sections: List<PromptSection>,
) {
    val text: String = sections.joinToString(SECTION_SEPARATOR) { it.text }

    val sectionNames: List<String> = sections.map { it.name }

    init {
        require(sections.isNotEmpty()) { "빈 프롬프트는 만들 수 없습니다." }
        require(sections.none { it.text.isBlank() }) {
            "내용이 빈 섹션이 있습니다: ${sections.filter { it.text.isBlank() }.map { it.name }}"
        }
    }
}

internal fun geminiPrompt(
    systemInstruction: String? = null,
    build: GeminiPromptSections.() -> Unit,
): GeminiPrompt = GeminiPrompt(systemInstruction, GeminiPromptSections().apply(build).sections)

internal class GeminiPromptSections {
    private val declared = mutableListOf<PromptSection>()

    val sections: List<PromptSection> get() = declared.toList()

    fun section(
        name: String,
        text: String,
    ) = section(PromptSection(name, text))

    fun section(section: PromptSection) {
        require(declared.none { it.name == section.name }) { "${section.name} 섹션을 두 번 넣었습니다." }
        declared += section
    }
}
