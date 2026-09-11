package com.github.nexters.ppotto.analysis.infrastructure

import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

internal inline fun <reified T : Any> objectSchema(
    description: String,
    properties: GeminiSchemaProperties<T>.() -> Unit,
): Schema {
    val declared = GeminiSchemaProperties<T>().apply(properties)
    val missing = T::class.memberProperties.map { it.name } - declared.names.toSet()
    require(missing.isEmpty()) {
        "${T::class.simpleName}의 ${missing.joinToString()} 필드가 Gemini 응답 스키마에 없어 항상 null로 파싱됩니다."
    }

    return Schema
        .builder()
        .type(Type.Known.OBJECT)
        .description(description)
        .properties(declared.schemas)
        .propertyOrdering(declared.names)
        .required(declared.requiredNames)
        .build()
}

internal fun arraySchema(
    description: String,
    items: Schema,
    minItems: Long? = null,
    maxItems: Long? = null,
): Schema =
    Schema
        .builder()
        .type(Type.Known.ARRAY)
        .description(description)
        .items(items)
        .apply {
            minItems?.let { minItems(it) }
            maxItems?.let { maxItems(it) }
        }.build()

internal fun stringSchema(description: String): Schema =
    Schema
        .builder()
        .type(Type.Known.STRING)
        .description(description)
        .build()

internal class GeminiSchemaProperties<T> {
    private val declared = LinkedHashMap<String, Schema>()
    private val optionalNames = mutableSetOf<String>()

    val schemas: Map<String, Schema> get() = declared
    val names: List<String> get() = declared.keys.toList()
    val requiredNames: List<String> get() = names - optionalNames

    fun string(
        property: KProperty1<T, *>,
        description: String,
        required: Boolean = true,
    ) = declare(property, stringSchema(description), required)

    fun number(
        property: KProperty1<T, *>,
        description: String,
    ) = declare(property, scalarSchema(Type.Known.NUMBER, description), required = true)

    fun boolean(
        property: KProperty1<T, *>,
        description: String,
    ) = declare(property, scalarSchema(Type.Known.BOOLEAN, description), required = true)

    fun strings(
        property: KProperty1<T, *>,
        description: String,
        itemDescription: String,
        minItems: Long? = null,
        maxItems: Long? = null,
    ) = declare(property, arraySchema(description, stringSchema(itemDescription), minItems, maxItems), required = true)

    fun objects(
        property: KProperty1<T, *>,
        description: String,
        items: Schema,
        minItems: Long? = null,
        maxItems: Long? = null,
    ) = declare(property, arraySchema(description, items, minItems, maxItems), required = true)

    fun nested(
        property: KProperty1<T, *>,
        schema: Schema,
    ) = declare(property, schema, required = true)

    private fun declare(
        property: KProperty1<T, *>,
        schema: Schema,
        required: Boolean,
    ) {
        require(declared.put(property.name, schema) == null) { "${property.name} 필드를 두 번 선언했습니다." }
        if (!required) optionalNames += property.name
    }

    private fun scalarSchema(
        type: Type.Known,
        description: String,
    ): Schema =
        Schema
            .builder()
            .type(type)
            .description(description)
            .build()
}
