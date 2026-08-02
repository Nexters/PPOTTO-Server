package com.github.nexters.ppotto.global.openapi

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * springdoc은 Kotlin 타입의 nullability를 읽지 않으므로, 이 프로젝트 DTO의 non-null 프로퍼티를
 * 생성된 스키마의 `required`에 자동 반영한다. DTO마다 requiredMode 어노테이션을 반복하지 않기 위한 장치.
 */
@Component
class KotlinRequiredModelConverter : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: Iterator<ModelConverter>,
    ): Schema<*>? {
        val resolved = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
        val rawClass =
            runCatching {
                Json
                    .mapper()
                    .constructType(type.type)
                    .rawClass
            }.getOrNull() ?: return resolved
        if (!rawClass.packageName.startsWith(BASE_PACKAGE)) return resolved
        val target = resolved?.let { schema -> schema.`$ref`?.let { context.definedModels[it.substringAfterLast('/')] } ?: schema }
        val properties = target?.properties ?: return resolved
        rawClass.kotlin.memberProperties
            .filterNot { it.returnType.isMarkedNullable }
            .map(::jsonName)
            .filter { it in properties && target.required?.contains(it) != true }
            .forEach(target::addRequiredItem)
        return resolved
    }

    private fun jsonName(property: KProperty1<*, *>): String {
        val annotations = property.getter.annotations + (property.javaField?.annotations ?: emptyArray())
        return annotations
            .filterIsInstance<JsonProperty>()
            .firstOrNull()
            ?.value
            ?.takeIf(String::isNotBlank)
            ?: property.name
    }

    private companion object {
        const val BASE_PACKAGE = "com.github.nexters.ppotto"
    }
}
