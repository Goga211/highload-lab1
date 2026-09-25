package ru.itmo.carsharing.common.web

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort

/**
 * Ограничения пагинации. size проверяется аннотациями @Min/@Max на параметре контроллера:
 * одного setMaxPageSize(50) мало, он молча режет 51 до 50, и клиент не видит ошибки.
 */
object Paging {
    const val MAX_PAGE_SIZE: Long = 50
    const val DEFAULT_PAGE: String = "0"
    const val DEFAULT_SIZE: String = "20"
    const val TOTAL_COUNT_HEADER: String = "X-Total-Count"

    fun of(page: Int, size: Int, sort: Sort = Sort.unsorted()): PageRequest = PageRequest.of(page, size, sort)
}

/** Страница с общим количеством записей в теле ответа. */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <E : Any, T> from(page: Page<E>, mapper: (E) -> T): PageResponse<T> = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}

/** Бесконечная прокрутка: без общего количества, только признак следующей страницы. */
data class SliceResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <E : Any, T> from(slice: Slice<E>, mapper: (E) -> T): SliceResponse<T> = SliceResponse(
            content = slice.content.map(mapper),
            page = slice.number,
            size = slice.size,
            hasNext = slice.hasNext(),
        )
    }
}
