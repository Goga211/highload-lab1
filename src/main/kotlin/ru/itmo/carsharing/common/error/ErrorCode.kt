package ru.itmo.carsharing.common.error

import org.springframework.http.HttpStatus

/**
 * Каталог ошибок API. slug превращается в поле type ответа по RFC 9457,
 * title одинаков для всех случаев одного типа, подробности идут в detail.
 */
enum class ErrorCode(val status: HttpStatus, val slug: String, val title: String) {
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Resource not found"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "bad-request", "Bad request"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "endpoint-not-found", "Endpoint not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "not-acceptable", "Not acceptable"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type"),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "duplicate-resource", "Resource already exists"),
    RESOURCE_IN_USE(HttpStatus.CONFLICT, "resource-in-use", "Resource is referenced by other data"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "invalid-status-transition", "Invalid status transition"),
    VEHICLE_NOT_AVAILABLE(HttpStatus.CONFLICT, "vehicle-not-available", "Vehicle is not available"),
    ACTIVE_RENTAL_EXISTS(HttpStatus.CONFLICT, "active-rental-exists", "Client already has an active rental"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "insufficient-stock", "Not enough spare parts in stock"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "concurrent-modification", "Resource was modified concurrently"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "data-conflict", "Data integrity conflict"),

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds", "Insufficient funds"),
    DRIVER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "driver-not-eligible", "Driver is not eligible"),
    LICENSE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "license-invalid", "Driver license is invalid"),
    USER_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "user-blocked", "User is blocked"),
    WRONG_USER_ROLE(HttpStatus.UNPROCESSABLE_CONTENT, "wrong-user-role", "User has an unsuitable role"),
    FINISH_ZONE_FORBIDDEN(HttpStatus.UNPROCESSABLE_CONTENT, "finish-zone-forbidden", "Finish is not allowed here"),
    TARIFF_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_CONTENT, "tariff-not-applicable", "Tariff is not applicable"),
    OPTION_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "option-unavailable", "Rental option is unavailable"),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "reservation-expired", "Reservation has expired"),
    INCOMPATIBLE_PART(HttpStatus.UNPROCESSABLE_CONTENT, "incompatible-part", "Spare part is incompatible"),
    TELEMETRY_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "telemetry-rejected", "Telemetry is rejected"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency key is reused"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "business-rule-violation", "Business rule violated"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error"),
}
