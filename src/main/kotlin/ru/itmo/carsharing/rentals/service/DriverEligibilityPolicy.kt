package ru.itmo.carsharing.rentals.service

import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.users.entity.LicenseCategory
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.entity.UserStatus
import ru.itmo.carsharing.users.service.DriverProfile
import java.time.LocalDate
import java.time.Period

data class EligibilityViolation(val code: ErrorCode, val message: String)

/**
 * Допуск к бронированию: активный клиент, одобренное и непросроченное ВУ с категорией B,
 * возраст от 18 лет. Для бизнес-класса возраст от 21 года и стаж от 2 лет.
 * Стаж считается от даты получения категории B, а не от даты выдачи текущей карточки.
 */
object DriverEligibilityPolicy {
    const val MIN_AGE = 18
    const val BUSINESS_MIN_AGE = 21
    const val BUSINESS_MIN_EXPERIENCE_YEARS = 2

    fun check(profile: DriverProfile, vehicleClass: VehicleClass, today: LocalDate): EligibilityViolation? {
        if (profile.status != UserStatus.ACTIVE) {
            return EligibilityViolation(ErrorCode.USER_BLOCKED, "Пользователь заблокирован")
        }
        if (profile.role != UserRole.CLIENT) {
            return EligibilityViolation(ErrorCode.WRONG_USER_ROLE, "Бронировать может только клиент")
        }
        val license = profile.approvedLicense
            ?: return EligibilityViolation(ErrorCode.LICENSE_INVALID, "Нет одобренного водительского удостоверения")
        if (license.expiresAt.isBefore(today)) {
            return EligibilityViolation(ErrorCode.LICENSE_INVALID, "Срок действия ВУ истёк ${license.expiresAt}")
        }
        if (LicenseCategory.B !in license.categories) {
            return EligibilityViolation(ErrorCode.LICENSE_INVALID, "В ВУ нет категории B")
        }
        val age = Period.between(profile.birthDate, today).years
        if (age < MIN_AGE) {
            return EligibilityViolation(ErrorCode.DRIVER_NOT_ELIGIBLE, "Водителю должно быть не меньше $MIN_AGE лет")
        }
        if (vehicleClass == VehicleClass.BUSINESS) {
            if (age < BUSINESS_MIN_AGE) {
                return EligibilityViolation(
                    ErrorCode.DRIVER_NOT_ELIGIBLE,
                    "Бизнес-класс доступен с $BUSINESS_MIN_AGE лет, водителю $age",
                )
            }
            val experience = Period.between(license.firstIssuedAt, today).years
            if (experience < BUSINESS_MIN_EXPERIENCE_YEARS) {
                return EligibilityViolation(
                    ErrorCode.DRIVER_NOT_ELIGIBLE,
                    "Бизнес-класс доступен при стаже от $BUSINESS_MIN_EXPERIENCE_YEARS лет, стаж $experience",
                )
            }
        }
        return null
    }
}
