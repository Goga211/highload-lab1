package ru.itmo.carsharing.common.error

import org.springframework.beans.TypeMismatchException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** Человекочитаемые сообщения для ошибок, которые Spring MVC выбрасывает до нашего кода. */
internal object FrameworkErrors {

    fun detail(ex: Exception): String? = when (ex) {
        is MissingServletRequestParameterException -> "Не передан обязательный параметр ${ex.parameterName}"
        is MissingRequestHeaderException -> "Не передан обязательный заголовок ${ex.headerName}"
        is MethodArgumentTypeMismatchException -> "Параметр ${ex.name} имеет недопустимое значение '${ex.value}'"
        is TypeMismatchException -> "Параметр ${ex.propertyName} имеет недопустимое значение '${ex.value}'"
        is NoResourceFoundException -> "Адрес /${ex.resourcePath} не найден"
        is HttpRequestMethodNotSupportedException -> "Метод ${ex.method} не поддерживается, допустимо: ${allowed(ex)}"
        is HttpMediaTypeNotSupportedException -> "Тип содержимого ${ex.contentType} не поддерживается, нужен JSON"
        is HttpMediaTypeNotAcceptableException -> "Ответ отдаётся только в application/json"
        else -> null
    }

    fun violations(ex: Exception): List<FieldViolation> = when (ex) {
        is MissingServletRequestParameterException -> listOf(FieldViolation(ex.parameterName, "обязательный параметр"))
        is MissingRequestHeaderException -> listOf(FieldViolation(ex.headerName, "обязательный заголовок"))
        is MethodArgumentTypeMismatchException -> listOf(FieldViolation(ex.name, "недопустимое значение"))
        else -> emptyList()
    }

    private fun allowed(ex: HttpRequestMethodNotSupportedException): String =
        ex.supportedMethods?.joinToString().orEmpty()
}
