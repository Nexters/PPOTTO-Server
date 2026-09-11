package com.github.nexters.ppotto.analysis.infrastructure

import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.withNullability

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class GeminiObject(
    val description: String,
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class GeminiField(
    val description: String,
    val itemDescription: String = "",
    val minItems: Int = 0,
    val maxItems: Int = 0,
    val required: Boolean = true,
)

internal inline fun <reified T : Any> geminiSchema(): Schema = geminiObjectSchema(T::class)

internal inline fun <reified T : Any> geminiListSchema(
    description: String,
    minItems: Int,
    maxItems: Int,
): Schema =
    Schema
        .builder()
        .type(Type.Known.ARRAY)
        .description(description)
        .items(geminiObjectSchema(T::class))
        .minItems(minItems.toLong())
        .maxItems(maxItems.toLong())
        .build()

internal fun geminiObjectSchema(type: KClass<*>): Schema {
    val description =
        type.findAnnotation<GeminiObject>()?.description
            ?: error("${type.simpleName}에 @GeminiObject 설명이 없습니다.")
    val parameters =
        type.primaryConstructor?.parameters
            ?: error("${type.simpleName}는 주 생성자가 없어 응답 스키마로 쓸 수 없습니다.")
    val fields = parameters.map { it to it.geminiField(type) }

    return Schema
        .builder()
        .type(Type.Known.OBJECT)
        .description(description)
        .properties(fields.associate { (parameter, field) -> parameter.schemaName to field.schemaOf(parameter.type) })
        .propertyOrdering(fields.map { (parameter, _) -> parameter.schemaName })
        .required(fields.filter { (_, field) -> field.required }.map { (parameter, _) -> parameter.schemaName })
        .build()
}

private val KParameter.schemaName: String
    get() = name ?: error("이름 없는 생성자 파라미터는 응답 스키마로 쓸 수 없습니다.")

private fun KParameter.geminiField(owner: KClass<*>): GeminiField =
    findAnnotation<GeminiField>() ?: error("${owner.simpleName}.$schemaName 에 @GeminiField 설명이 없습니다.")

private fun GeminiField.schemaOf(type: KType): Schema {
    val classifier = type.withNullability(false).classifier as? KClass<*> ?: error("지원하지 않는 응답 필드 타입입니다: $type")
    if (classifier == List::class) return listSchemaOf(type)

    return scalarSchema(classifier, description)
}

private fun GeminiField.listSchemaOf(type: KType): Schema {
    val itemType =
        type.arguments
            .single()
            .type
            ?: error("원소 타입을 알 수 없는 리스트는 응답 스키마로 쓸 수 없습니다: $type")
    val itemClassifier = itemType.withNullability(false).classifier as? KClass<*> ?: error("지원하지 않는 원소 타입입니다: $itemType")
    val items =
        if (itemClassifier.findAnnotation<GeminiObject>() != null) {
            geminiObjectSchema(itemClassifier)
        } else {
            require(itemDescription.isNotBlank()) { "$description 리스트에 itemDescription이 없습니다." }
            scalarSchema(itemClassifier, itemDescription)
        }

    return Schema
        .builder()
        .type(Type.Known.ARRAY)
        .description(description)
        .items(items)
        .apply {
            if (minItems > 0) minItems(minItems.toLong())
            if (maxItems > 0) maxItems(maxItems.toLong())
        }.build()
}

private fun scalarSchema(
    classifier: KClass<*>,
    description: String,
): Schema {
    if (classifier.findAnnotation<GeminiObject>() != null) return geminiObjectSchema(classifier)

    val type =
        when (classifier) {
            String::class -> Type.Known.STRING
            Boolean::class -> Type.Known.BOOLEAN
            Double::class, Float::class, Int::class, Long::class -> Type.Known.NUMBER
            else -> error("지원하지 않는 응답 필드 타입입니다: ${classifier.simpleName}")
        }

    return Schema
        .builder()
        .type(type)
        .description(description)
        .build()
}
