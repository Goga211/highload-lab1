package ru.itmo.carsharing.common.web

/**
 * Коды ошибок, которые может вернуть эндпоинт, кроме 400 (есть у всех) и 404
 * (добавляется сам, если в пути есть идентификатор). Попадают в OpenAPI с телом RFC 9457.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ApiErrors(vararg val value: Int)
