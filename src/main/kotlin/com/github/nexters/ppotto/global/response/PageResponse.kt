package com.github.nexters.ppotto.global.response

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> of(items: List<T>, page: Int, size: Int, totalCount: Long): PageResponse<T> =
            PageResponse(items, page, size, totalCount, (page + 1).toLong() * size < totalCount)
    }
}
