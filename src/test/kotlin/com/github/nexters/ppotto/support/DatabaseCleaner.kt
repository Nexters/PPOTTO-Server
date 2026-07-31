package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.RECAP_COMMENTS
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.jooq.tables.references.STICKER_PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.Table
import org.springframework.stereotype.Component

@Component
class DatabaseCleaner(
    private val dsl: DSLContext,
) {
    init {
        instance = this
    }

    private fun truncateAll() =
        TRUNCATION_ORDER.forEach {
            dsl
                .truncate(it)
                .cascade()
                .execute()
        }

    companion object {
        private val TRUNCATION_ORDER: List<Table<*>> =
            listOf(
                RECAP_COMMENTS,
                STICKER_PHOTOS,
                DRAWINGS,
                STICKERS,
                PHOTOS,
                ANALYSIS,
                BOARDS,
                TERM_AGREEMENTS,
                TERMS,
                USERS,
            )

        private lateinit var instance: DatabaseCleaner

        fun clear() = instance.truncateAll()
    }
}
