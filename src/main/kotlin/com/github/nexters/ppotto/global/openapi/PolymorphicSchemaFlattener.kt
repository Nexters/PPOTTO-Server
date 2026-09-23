package com.github.nexters.ppotto.global.openapi

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation

/**
 * `@JsonSubTypes` 다형 타입을 코드 생성기가 판별 union으로 읽을 수 있게 다듬는다.
 *
 * - 부모: `oneOf` + `discriminator.mapping`만 남기고 형제 `properties`는 제거한다.
 * - 자식: `allOf` 부모 참조를 풀어 평탄한 객체로 만들고 판별자 상수 프로퍼티를 추가한다.
 */
internal object PolymorphicSchemaFlattener {
    fun flatten(
        rawClass: Class<*>,
        schema: Schema<*>,
    ) {
        rawClass.getAnnotation(JsonSubTypes::class.java)?.let { exposeAsUnion(rawClass, it, schema) }
        polymorphicParentOf(rawClass)?.let { (parent, name) -> exposeAsMember(parent, name, schema) }
    }

    private fun exposeAsUnion(
        parent: Class<*>,
        subTypes: JsonSubTypes,
        schema: Schema<*>,
    ) {
        val members = subTypes.value.associate { it.name to schemaRefOf(it.value.java) }
        schema.oneOf = members.values.map { Schema<Any>().`$ref`(it) }
        schema.discriminator = Discriminator().propertyName(discriminatorPropertyOf(parent)).mapping(members)
        schema.properties = null
        schema.required = null
        schema.type = null
        schema.types = null
    }

    private fun exposeAsMember(
        parent: Class<*>,
        name: String,
        schema: Schema<*>,
    ) {
        val own = schema.allOf?.firstOrNull { it.`$ref` == null } ?: return
        schema.allOf = null
        schema.type = own.type
        schema.types = own.types
        schema.properties = own.properties
        schema.required = own.required
        val property = discriminatorPropertyOf(parent)
        schema.addProperty(
            property,
            Schema<String>()
                .type("string")
                .types(setOf("string"))
                ._enum(listOf(name))
                .description("판별자. 항상 $name"),
        )
        schema.addRequiredItem(property)
    }

    private fun polymorphicParentOf(rawClass: Class<*>): Pair<Class<*>, String>? =
        (rawClass.interfaces.toList() + listOfNotNull(rawClass.superclass)).firstNotNullOfOrNull { parent ->
            parent
                .getAnnotation(JsonSubTypes::class.java)
                ?.value
                ?.firstOrNull { it.value.java == rawClass }
                ?.let { parent to it.name }
        }

    private fun discriminatorPropertyOf(parent: Class<*>): String =
        parent.getAnnotation(JsonTypeInfo::class.java)?.let { it.property.ifBlank { it.use.defaultPropertyName } }
            ?: DEFAULT_DISCRIMINATOR

    private fun schemaRefOf(clazz: Class<*>): String {
        val name =
            clazz
                .getAnnotation(SchemaAnnotation::class.java)
                ?.name
                ?.takeIf(String::isNotBlank) ?: clazz.simpleName
        return "$SCHEMA_REF_PREFIX$name"
    }

    private const val SCHEMA_REF_PREFIX = "#/components/schemas/"
    private const val DEFAULT_DISCRIMINATOR = "type"
}
