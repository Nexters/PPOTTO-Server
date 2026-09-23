package com.github.nexters.ppotto.global.openapi

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.PpottoApplication
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * 프로젝트 DTO 스키마를 코드 생성기(openapi-typescript 등)가 그대로 소비할 수 있는 형태로 다듬는다.
 *
 * 1. Kotlin non-null 프로퍼티를 `required`로 표시한다.
 * 2. value class 프로퍼티의 JVM 맹글링 접미사(`id-YTcRAVk`)를 원래 JSON 이름으로 되돌린다.
 * 3. `@JsonSubTypes` 다형 타입을 판별 union으로 노출한다 ([PolymorphicSchemaFlattener]).
 */
@Component
class KotlinRequiredModelConverter : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: Iterator<ModelConverter>,
    ): Schema<*>? {
        val resolved = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
        val projectClass = projectClassOf(type)
        val schema = resolved.definitionIn(context)
        if (projectClass != null && schema != null) shape(projectClass, schema)
        return resolved
    }

    private fun shape(
        rawClass: Class<*>,
        schema: Schema<*>,
    ) {
        PolymorphicSchemaFlattener.flatten(rawClass, schema)
        restoreValueClassPropertyNames(rawClass, schema)
        markNonNullRequired(rawClass, schema)
    }

    private fun projectClassOf(type: AnnotatedType): Class<*>? =
        runCatching {
            Json
                .mapper()
                .constructType(type.type)
                .rawClass
        }.getOrNull()
            ?.takeIf { it.packageName.startsWith(BASE_PACKAGE) }

    private fun Schema<*>?.definitionIn(context: ModelConverterContext): Schema<*>? =
        this?.let { schema ->
            schema.`$ref`?.let { context.definedModels[it.substringAfterLast('/')] } ?: schema
        }

    private fun restoreValueClassPropertyNames(
        rawClass: Class<*>,
        schema: Schema<*>,
    ) {
        val properties = schema.properties ?: return
        val renames =
            rawClass.kotlin.memberProperties
                .filter { (it.returnType.classifier as? KClass<*>)?.isValue == true }
                .map(::jsonName)
                .filter { it !in properties }
                .mapNotNull { name ->
                    properties.keys
                        .firstOrNull { it.startsWith("$name-") }
                        ?.let { it to name }
                }.toMap()
        if (renames.isEmpty()) return
        schema.properties =
            properties.entries.associateTo(LinkedHashMap<String, Schema<*>>()) { (key, value) ->
                (renames[key] ?: key) to value
            }
        schema.required = schema.required?.map { renames[it] ?: it }
    }

    private fun markNonNullRequired(
        rawClass: Class<*>,
        schema: Schema<*>?,
    ) {
        val properties = schema?.properties ?: return
        rawClass.kotlin.memberProperties
            .filterNot { it.returnType.isMarkedNullable }
            .map(::jsonName)
            .filter { it in properties && schema.required?.contains(it) != true }
            .forEach(schema::addRequiredItem)
    }

    private fun jsonName(property: KProperty1<*, *>): String =
        property
            .declaredAnnotations()
            .filterIsInstance<JsonProperty>()
            .firstOrNull()
            ?.value
            ?.takeIf(String::isNotBlank)
            ?: property.name

    private fun KProperty1<*, *>.declaredAnnotations(): List<Annotation> {
        val fieldAnnotations = javaField?.annotations.orEmpty()
        return getter.annotations + fieldAnnotations
    }

    private companion object {
        val BASE_PACKAGE: String = PpottoApplication::class.java.packageName
    }
}
