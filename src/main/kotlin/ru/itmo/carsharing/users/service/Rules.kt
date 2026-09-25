package ru.itmo.carsharing.users.service

import ru.itmo.carsharing.users.entity.LicenseCategory
import java.time.LocalDate

object UserRules {
    const val PHONE_REGEX = "^\\+?[0-9]{10,15}$"
    const val MIN_USER_AGE = 18
}

data class RuleViolation(val field: String, val message: String)

/**
 * Проверки ВУ, которые не требуют базы: формат номера и согласованность дат.
 * Подлинность документа подтверждает саппорт вручную.
 */
object LicenseRules {
    /** Во входных данных допускаем пробелы и строчные буквы: "77 АВ 123456". */
    const val NUMBER_INPUT_REGEX = "^\\d{2}\\s?(\\d{2}|[АВЕКМНОРСТУХавекмнорстух]{2})\\s?\\d{6}$"

    /** Хранится нормализованный номер: 2 цифры региона, серия из 2 цифр или 2 букв, 6 цифр. */
    const val NUMBER_REGEX = "^\\d{2}(\\d{2}|[АВЕКМНОРСТУХ]{2})\\d{6}$"

    /** 10 лет по закону плюс 3 года продления для прав, истекавших в 2022-2025 годах. */
    const val MAX_VALIDITY_YEARS: Long = 13
    const val MIN_DRIVER_AGE: Long = 18

    fun normalizeNumber(raw: String): String = raw.replace(" ", "").uppercase()

    fun checkDates(
        issuedAt: LocalDate,
        expiresAt: LocalDate,
        firstIssuedAt: LocalDate,
        categories: Set<LicenseCategory>,
    ): List<RuleViolation> = buildList {
        if (!expiresAt.isAfter(issuedAt)) {
            add(RuleViolation("expiresAt", "срок действия должен заканчиваться позже даты выдачи"))
        }
        if (expiresAt.isAfter(issuedAt.plusYears(MAX_VALIDITY_YEARS))) {
            add(RuleViolation("expiresAt", "срок действия не может превышать $MAX_VALIDITY_YEARS лет"))
        }
        if (firstIssuedAt.isAfter(issuedAt)) {
            add(RuleViolation("firstIssuedAt", "дата получения категории B не может быть позже даты выдачи"))
        }
        if (LicenseCategory.B !in categories) {
            add(RuleViolation("categories", "для аренды нужна категория B"))
        }
    }

    /** Стаж отсчитывается от даты получения категории B, её не могли получить раньше 18 лет. */
    fun checkAgainstBirthDate(firstIssuedAt: LocalDate, birthDate: LocalDate): RuleViolation? =
        if (firstIssuedAt.isBefore(birthDate.plusYears(MIN_DRIVER_AGE))) {
            RuleViolation("firstIssuedAt", "категорию B нельзя получить раньше $MIN_DRIVER_AGE лет")
        } else {
            null
        }
}
