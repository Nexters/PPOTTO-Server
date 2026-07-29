package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class DatabaseCleaner(
    private val dsl: DSLContext,
) {
    init {
        instance = this
    }

    private fun truncateAll() {
        dsl.truncate(PHOTOS).cascade().execute()
        dsl.truncate(ANALYSIS).cascade().execute()
        dsl.truncate(BOARDS).cascade().execute()
        dsl.truncate(USERS).cascade().execute()
    }

    companion object {
        private lateinit var instance: DatabaseCleaner

        fun clear() = instance.truncateAll()
    }
}
