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

@Component
class KotlinRequiredModelConverter : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: Iterator<ModelConverter>,
    ): Schema<*>? =
        (if (chain.hasNext()) chain.next().resolve(type, context, chain) else null)
            .also { resolved ->
                projectClassOf(type)?.let { markNonNullRequired(it, resolved.definitionIn(context)) }
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

    private fun markNonNullRequired(
        rawClass: Class<*>,
        schema: Schema<*>?,
    ) = schema?.properties?.let { properties ->
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
        const val BASE_PACKAGE = "com.github.nexters.ppotto"
    }
}
