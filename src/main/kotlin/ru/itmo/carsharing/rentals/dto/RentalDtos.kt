package ru.itmo.carsharing.rentals.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.rentals.entity.FineStatus
import ru.itmo.carsharing.rentals.entity.OptionPriceUnit
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.entity.TariffStatus
import ru.itmo.carsharing.rentals.service.FineRules
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private const val MAX_OPTIONS = 10
private const val MAX_OPTION_QUANTITY: Long = 10
private const val MAX_TARIFF_MODELS = 200

data class TariffRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val pricePerMinute: BigDecimal,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val pricePerKm: BigDecimal,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val waitingPricePerMinute: BigDecimal,

    @field:Min(0)
    @field:Max(120)
    val freeReservationMinutes: Int,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val depositAmount: BigDecimal,

    val validFrom: Instant,
    val validTo: Instant? = null,

    @field:Size(max = MAX_TARIFF_MODELS)
    val modelIds: Set<UUID> = emptySet(),
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "validTo должен быть позже validFrom")
    val isValidityRangeCorrect: Boolean
        get() = validTo == null || validTo.isAfter(validFrom)
}

data class TariffResponse(
    val id: UUID,
    val name: String,
    val pricePerMinute: BigDecimal,
    val pricePerKm: BigDecimal,
    val waitingPricePerMinute: BigDecimal,
    val freeReservationMinutes: Int,
    val depositAmount: BigDecimal,
    val validFrom: Instant,
    val validTo: Instant?,
    val status: TariffStatus,
    val modelIds: Set<UUID>,
)

data class TariffModelsRequest(
    @field:NotEmpty
    @field:Size(max = MAX_TARIFF_MODELS)
    val modelIds: Set<UUID>,
)

data class RentalOptionRequest(
    @field:Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "код: заглавные латинские буквы, цифры и _")
    val code: String,

    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val price: BigDecimal,

    val priceUnit: OptionPriceUnit,

    val active: Boolean = true,
)

data class RentalOptionResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val price: BigDecimal,
    val priceUnit: OptionPriceUnit,
    val active: Boolean,
)

data class OptionSelection(
    val optionId: UUID,

    @field:Min(1)
    @field:Max(MAX_OPTION_QUANTITY)
    val quantity: Int = 1,
)

/** В ЛР1 клиент передаётся в теле, в ЛР3 он возьмётся из токена. */
data class CreateRentalRequest(
    val userId: UUID,
    val vehicleId: UUID,
    val tariffId: UUID? = null,

    @field:Valid
    @field:Size(max = MAX_OPTIONS)
    val options: List<OptionSelection> = emptyList(),
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "опции не должны повторяться")
    val isOptionsDistinct: Boolean
        get() = options.map { it.optionId }.toSet().size == options.size
}

data class CancelRentalRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class AppliedRatesResponse(
    val pricePerMinute: BigDecimal,
    val pricePerKm: BigDecimal,
    val waitingPricePerMinute: BigDecimal,
    val freeReservationMinutes: Int,
    val deposit: BigDecimal,
)

data class RentalOptionItemResponse(
    val optionId: UUID,
    val code: String,
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val priceUnit: OptionPriceUnit,
    val totalAmount: BigDecimal?,
)

data class RentalCostResponse(
    val durationMinutes: Int?,
    val waitingMinutes: Int?,
    val distanceKm: Int?,
    val optionsAmount: BigDecimal?,
    val zoneSurcharge: BigDecimal?,
    val totalAmount: BigDecimal?,
)

data class RentalResponse(
    val id: UUID,
    val userId: UUID,
    val vehicleId: UUID,
    val tariffId: UUID,
    val status: RentalStatus,
    val reservedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val startZoneId: UUID?,
    val finishZoneId: UUID?,
    val startOdometerKm: Int?,
    val finishOdometerKm: Int?,
    val rates: AppliedRatesResponse,
    val cost: RentalCostResponse,
    val options: List<RentalOptionItemResponse>?,
)

data class FineRequest(
    val vehicleId: UUID,

    @field:Pattern(regexp = FineRules.RESOLUTION_NUMBER_REGEX, message = "номер постановления: 20 или 25 цифр")
    val resolutionNumber: String,

    @field:PastOrPresent
    val violatedAt: Instant,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal,
)

data class DisputeFineRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class FineResponse(
    val id: UUID,
    val vehicleId: UUID,
    val rentalId: UUID?,
    val paymentId: UUID?,
    val resolutionNumber: String,
    val violatedAt: Instant,
    val amount: BigDecimal,
    val status: FineStatus,
    val disputeReason: String?,
    val createdAt: Instant,
)
