package ru.itmo.carsharing.common.web

import org.springframework.http.ResponseEntity
import java.net.URI

/** 201 Created с заголовком Location на созданный ресурс. */
fun <T : Any> created(location: String, body: T): ResponseEntity<T> =
    ResponseEntity.created(URI.create(location)).body(body)

/** Список с общим количеством в заголовке X-Total-Count. */
fun <T : Any> withTotalCount(page: PageResponse<T>): ResponseEntity<List<T>> =
    ResponseEntity.ok()
        .header(Paging.TOTAL_COUNT_HEADER, page.totalElements.toString())
        .body(page.content)

object ApiPaths {
    const val BASE = "/api/v1"
}
