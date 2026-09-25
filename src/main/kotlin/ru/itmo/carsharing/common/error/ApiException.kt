package ru.itmo.carsharing.common.error

/**
 * Исключение бизнес-уровня. Обработчик превращает его в ProblemDetail
 * со статусом и типом из [code] и человекочитаемым [detail].
 */
open class ApiException(val code: ErrorCode, val detail: String) : RuntimeException(detail)

class NotFoundException(resource: String, id: Any) :
    ApiException(ErrorCode.NOT_FOUND, "$resource с идентификатором $id не найден")

fun conflict(code: ErrorCode, detail: String): Nothing = throw ApiException(code, detail)

fun unprocessable(code: ErrorCode, detail: String): Nothing = throw ApiException(code, detail)

fun invalidTransition(resource: String, from: Enum<*>, to: Enum<*>): Nothing =
    throw ApiException(
        ErrorCode.INVALID_STATUS_TRANSITION,
        "$resource: переход из статуса $from в $to невозможен",
    )
