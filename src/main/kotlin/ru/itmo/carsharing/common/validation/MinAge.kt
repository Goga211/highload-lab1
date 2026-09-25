package ru.itmo.carsharing.common.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.LocalDate
import kotlin.reflect.KClass

/** Дата рождения, при которой человеку исполнилось не меньше [value] лет. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MinAgeValidator::class])
@MustBeDocumented
annotation class MinAge(
    val value: Int,
    val message: String = "возраст должен быть не меньше {value} лет",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MinAgeValidator : ConstraintValidator<MinAge, LocalDate> {

    private var years: Long = 0

    override fun initialize(annotation: MinAge) {
        years = annotation.value.toLong()
    }

    override fun isValid(value: LocalDate?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val today = LocalDate.now(context.clockProvider.clock)
        return !value.plusYears(years).isAfter(today)
    }
}
