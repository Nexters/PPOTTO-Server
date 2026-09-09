package com.github.nexters.ppotto.global.storage

import java.util.UUID

class ObjectKeyGenerator {
    fun prefix(vararg pathSegments: String): String = pathSegments.joinToString("/", postfix = "/")

    fun generate(
        vararg pathSegments: String,
        id: UUID,
        extension: String,
    ): String = "${prefix(*pathSegments)}$id.$extension"
}
