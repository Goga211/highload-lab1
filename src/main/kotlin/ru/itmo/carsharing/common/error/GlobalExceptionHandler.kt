package ru.itmo.carsharing.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.transaction.TransactionSystemException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

data class FieldViolation(val field: String, val message: String)

/**
 * Все ошибки контроллеров уходят клиенту в формате RFC 9457 (application/problem+json).
 * Для ошибок валидации в поле errors перечисляются поля с сообщениями.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        respond(ex.code, ex.detail, request.requestURI)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> = respond(
        ErrorCode.VALIDATION_FAILED,
        "Данные не прошли проверку",
        request.requestURI,
        ex.constraintViolations.map { FieldViolation(it.propertyPath.toString(), it.message) },
    )

    @ExceptionHandler(TransactionSystemException::class)
    fun handleTransactionSystem(
        ex: TransactionSystemException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val violation = generateSequence(ex as Throwable) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .firstOrNull()
            ?: return handleUnexpected(ex, request)
        return handleConstraintViolation(violation, request)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val constraint = ConstraintMessages.find(ex)
        log.debug("Data integrity violation, constraint={}", constraint?.name, ex)
        return if (constraint != null) {
            respond(constraint.code, constraint.message, request.requestURI)
        } else {
            respond(ErrorCode.DATA_CONFLICT, "Операция нарушает целостность данных", request.requestURI)
        }
    }

    @ExceptionHandler(OptimisticLockingFailureException::class, PessimisticLockingFailureException::class)
    fun handleLockingFailure(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        respond(
            ErrorCode.CONCURRENT_MODIFICATION,
            "Запись одновременно изменили в другом запросе, повторите операцию",
            request.requestURI,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("Unexpected error on {} {}", request.method, request.requestURI, ex)
        return respond(ErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера", request.requestURI)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.allErrors.map { error ->
            val field = (error as? FieldError)?.field ?: error.objectName
            FieldViolation(field, error.defaultMessage ?: "некорректное значение")
        }
        return toResponse(ErrorCode.VALIDATION_FAILED, "Поля запроса не прошли проверку", request, errors)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.parameterValidationResults.flatMap { result ->
            val name = result.methodParameter.parameterName ?: "parameter"
            result.resolvableErrors.map { FieldViolation(name, it.defaultMessage ?: "некорректное значение") }
        }
        return toResponse(ErrorCode.VALIDATION_FAILED, "Параметры запроса не прошли проверку", request, errors)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? = toResponse(
        ErrorCode.MALFORMED_REQUEST,
        "Тело запроса не удалось разобрать: проверьте JSON и обязательные поля",
        request,
    )

    /**
     * Ошибки уровня Spring MVC (нет параметра, неизвестный путь, не тот метод или Content-Type)
     * приводятся к тому же виду, что и наши: свой type, русский detail и поле errors.
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        // Для ошибок Spring тело собирается внутри super из ErrorResponse, поэтому дополняем результат.
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val problem = response?.body as? ProblemDetail
        if (problem != null && (problem.type == null || problem.type == BLANK_TYPE)) {
            val code = frameworkCode(statusCode)
            problem.type = URI.create("$ERROR_TYPE_BASE/${code.slug}")
            problem.title = code.title
            FrameworkErrors.detail(ex)?.let { problem.detail = it }
            problem.setProperty("errors", FrameworkErrors.violations(ex))
        }
        return response
    }

    private fun frameworkCode(status: HttpStatusCode): ErrorCode = when (status.value()) {
        ErrorCode.ENDPOINT_NOT_FOUND.status.value() -> ErrorCode.ENDPOINT_NOT_FOUND
        ErrorCode.METHOD_NOT_ALLOWED.status.value() -> ErrorCode.METHOD_NOT_ALLOWED
        ErrorCode.NOT_ACCEPTABLE.status.value() -> ErrorCode.NOT_ACCEPTABLE
        ErrorCode.UNSUPPORTED_MEDIA_TYPE.status.value() -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
        else -> if (status.is4xxClientError) ErrorCode.BAD_REQUEST else ErrorCode.INTERNAL_ERROR
    }

    private fun toResponse(
        code: ErrorCode,
        detail: String,
        request: WebRequest,
        errors: List<FieldViolation> = emptyList(),
    ): ResponseEntity<Any> {
        val path = (request as? ServletWebRequest)?.request?.requestURI
        return ResponseEntity.status(code.status).body(problem(code, detail, path, errors))
    }

    private fun respond(
        code: ErrorCode,
        detail: String,
        path: String,
        errors: List<FieldViolation> = emptyList(),
    ): ResponseEntity<ProblemDetail> = ResponseEntity.status(code.status).body(problem(code, detail, path, errors))

    private fun problem(code: ErrorCode, detail: String, path: String?, errors: List<FieldViolation>): ProblemDetail =
        ProblemDetail.forStatusAndDetail(code.status, detail).apply {
            type = URI.create("$ERROR_TYPE_BASE/${code.slug}")
            title = code.title
            path?.let { instance = URI.create(it) }
            setProperty("errors", errors)
        }

    companion object {
        const val ERROR_TYPE_BASE = "https://carsharing.local/errors"
        private val BLANK_TYPE: URI = URI.create("about:blank")
    }
}
