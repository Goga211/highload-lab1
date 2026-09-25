package ru.itmo.carsharing.users.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.users.entity.LicenseCategory
import ru.itmo.carsharing.users.entity.LicenseVerificationStatus
import ru.itmo.carsharing.users.service.LicenseRules
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

@ConsistentLicenseDates
data class AddLicenseRequest(
    @field:Pattern(
        regexp = LicenseRules.NUMBER_INPUT_REGEX,
        message = "номер ВУ: 2 цифры региона, серия из 2 цифр или 2 букв, 6 цифр",
    )
    val number: String,

    @field:PastOrPresent
    val issuedAt: LocalDate,

    @field:FutureOrPresent(message = "срок действия ВУ истёк")
    val expiresAt: LocalDate,

    @field:PastOrPresent
    val firstIssuedAt: LocalDate,

    @field:NotEmpty
    val categories: Set<LicenseCategory>,
)

data class ApproveLicenseRequest(val verifierId: UUID)

data class RejectLicenseRequest(
    val verifierId: UUID,

    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class LicenseResponse(
    val id: UUID,
    val userId: UUID,
    val number: String,
    val issuedAt: LocalDate,
    val expiresAt: LocalDate,
    val firstIssuedAt: LocalDate,
    val categories: Set<LicenseCategory>,
    val verificationStatus: LicenseVerificationStatus,
    val verifiedBy: UUID?,
    val verifiedAt: Instant?,
    val rejectionReason: String?,
    val createdAt: Instant,
)

/** Согласованность дат и категорий ВУ, см. [LicenseRules.checkDates]. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ConsistentLicenseDatesValidator::class])
@MustBeDocumented
annotation class ConsistentLicenseDates(
    val message: String = "даты ВУ не согласованы",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ConsistentLicenseDatesValidator : ConstraintValidator<ConsistentLicenseDates, AddLicenseRequest> {
    override fun isValid(value: AddLicenseRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val violations = LicenseRules.checkDates(value.issuedAt, value.expiresAt, value.firstIssuedAt, value.categories)
        if (violations.isEmpty()) return true
        context.disableDefaultConstraintViolation()
        violations.forEach {
            context.buildConstraintViolationWithTemplate(it.message)
                .addPropertyNode(it.field)
                .addConstraintViolation()
        }
        return false
    }
}
