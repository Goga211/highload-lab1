package ru.itmo.carsharing.common.error

import org.springframework.dao.DataIntegrityViolationException

/**
 * Перевод нарушений ограничений базы в понятные ошибки API.
 * Сервисы проверяют правила заранее, а база страхует от гонок: если два запроса
 * прошли проверку одновременно, второй упрётся в уникальный индекс и получит тот же ответ.
 */
internal object ConstraintMessages {

    data class Mapping(val name: String, val code: ErrorCode, val message: String)

    private val mappings = listOf(
        Mapping("ux_rental_active_vehicle", ErrorCode.VEHICLE_NOT_AVAILABLE, "Машина уже занята другой арендой"),
        Mapping("ux_rental_active_user", ErrorCode.ACTIVE_RENTAL_EXISTS, "У клиента уже есть незавершённая аренда"),
        Mapping(
            "ux_driver_license_approved_per_user",
            ErrorCode.INVALID_STATUS_TRANSITION,
            "У клиента уже есть одобренное ВУ",
        ),
        Mapping(
            "ux_maintenance_task_open_per_type",
            ErrorCode.DUPLICATE_RESOURCE,
            "У машины уже есть открытый наряд этого типа",
        ),
        Mapping("ux_payment_idempotency_key", ErrorCode.DUPLICATE_RESOURCE, "Операция с таким ключом уже выполнена"),
        Mapping("uq_app_user_email", ErrorCode.DUPLICATE_RESOURCE, "Пользователь с таким email уже существует"),
        Mapping("uq_app_user_phone", ErrorCode.DUPLICATE_RESOURCE, "Пользователь с таким телефоном уже существует"),
        Mapping("uq_driver_license_number", ErrorCode.DUPLICATE_RESOURCE, "ВУ с таким номером уже зарегистрировано"),
        Mapping("uq_vehicle_vin", ErrorCode.DUPLICATE_RESOURCE, "Машина с таким VIN уже есть"),
        Mapping("uq_vehicle_plate_number", ErrorCode.DUPLICATE_RESOURCE, "Машина с таким госномером уже есть"),
        Mapping("uq_vehicle_model_brand_model", ErrorCode.DUPLICATE_RESOURCE, "Такая модель уже есть в справочнике"),
        Mapping("uq_parking_zone_name", ErrorCode.DUPLICATE_RESOURCE, "Зона с таким названием уже есть"),
        Mapping("uq_spare_part_article", ErrorCode.DUPLICATE_RESOURCE, "Запчасть с таким артикулом уже есть"),
        Mapping("uq_rental_option_code", ErrorCode.DUPLICATE_RESOURCE, "Опция с таким кодом уже есть"),
        Mapping(
            "uq_traffic_fine_resolution_number",
            ErrorCode.DUPLICATE_RESOURCE,
            "Постановление с таким номером уже введено",
        ),
        Mapping("ck_spare_part_stock", ErrorCode.INSUFFICIENT_STOCK, "На складе не хватает запчастей"),
    )

    fun find(ex: DataIntegrityViolationException): Mapping? {
        val hibernateName = generateSequence(ex as Throwable) { it.cause }
            .filterIsInstance<org.hibernate.exception.ConstraintViolationException>()
            .firstNotNullOfOrNull { it.constraintName }
        val text = hibernateName ?: generateSequence(ex as Throwable) { it.cause }.mapNotNull { it.message }
            .joinToString(" ")
        return mappings.firstOrNull { text.contains(it.name) }
    }
}
