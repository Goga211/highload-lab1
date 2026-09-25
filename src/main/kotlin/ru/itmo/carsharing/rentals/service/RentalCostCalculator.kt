package ru.itmo.carsharing.rentals.service

import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.rentals.entity.AppliedRates
import ru.itmo.carsharing.rentals.entity.OptionPriceUnit
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

data class OptionCharge(val unitPrice: BigDecimal, val unit: OptionPriceUnit, val quantity: Int)

data class CostInput(
    val reservedAt: Instant,
    val startedAt: Instant,
    val finishedAt: Instant,
    val startOdometerKm: Int,
    val finishOdometerKm: Int,
    val rates: AppliedRates,
    val options: List<OptionCharge>,
    val zoneSurcharge: BigDecimal,
)

data class RentalCost(
    val durationMinutes: Int,
    val waitingMinutes: Int,
    val distanceKm: Int,
    val rideAmount: BigDecimal,
    val distanceAmount: BigDecimal,
    val waitingAmount: BigDecimal,
    val optionTotals: List<BigDecimal>,
    val optionsAmount: BigDecimal,
    val zoneSurcharge: BigDecimal,
    val total: BigDecimal,
)

/**
 * Стоимость поездки из раздела "Бизнес-правила":
 *   минуты    = каждая начатая минута от старта до финиша
 *   ожидание  = max(0, минуты от брони до старта - бесплатные минуты брони)
 *   километры = одометр на финише - одометр на старте
 *   стоимость = минуты * ставка + километры * ставка + ожидание * ставка + опции + доплата за зону
 * Опции PER_RENTAL стоят цена * количество, PER_MINUTE стоят цена * минуты * количество.
 */
object RentalCostCalculator {

    private const val MILLIS_PER_MINUTE = 60_000L

    fun calculate(input: CostInput): RentalCost {
        val durationMinutes = maxOf(1, startedMinutes(input.startedAt, input.finishedAt))
        val waitingMinutes = maxOf(0, startedMinutes(input.reservedAt, input.startedAt) - input.rates.freeReservationMinutes)
        val distanceKm = maxOf(0, input.finishOdometerKm - input.startOdometerKm)

        val rideAmount = Money.of(input.rates.pricePerMinute * BigDecimal(durationMinutes))
        val distanceAmount = Money.of(input.rates.pricePerKm * BigDecimal(distanceKm))
        val waitingAmount = Money.of(input.rates.waitingPricePerMinute * BigDecimal(waitingMinutes))
        val optionTotals = input.options.map { optionTotal(it, durationMinutes) }
        val optionsAmount = optionTotals.fold(Money.ZERO, BigDecimal::add)
        val zoneSurcharge = Money.of(input.zoneSurcharge)
        val total = Money.of(rideAmount + distanceAmount + waitingAmount + optionsAmount + zoneSurcharge)

        return RentalCost(
            durationMinutes = durationMinutes,
            waitingMinutes = waitingMinutes,
            distanceKm = distanceKm,
            rideAmount = rideAmount,
            distanceAmount = distanceAmount,
            waitingAmount = waitingAmount,
            optionTotals = optionTotals,
            optionsAmount = optionsAmount,
            zoneSurcharge = zoneSurcharge,
            total = total,
        )
    }

    /** Каждая начатая минута: 45 минут ровно это 45, 45 минут и 1 секунда уже 46. */
    fun startedMinutes(from: Instant, to: Instant): Int {
        val millis = Duration.between(from, to).toMillis()
        if (millis <= 0) return 0
        return ((millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
    }

    private fun optionTotal(option: OptionCharge, minutes: Int): BigDecimal {
        val units = when (option.unit) {
            OptionPriceUnit.PER_RENTAL -> option.quantity
            OptionPriceUnit.PER_MINUTE -> option.quantity * minutes
        }
        return Money.of(option.unitPrice * BigDecimal(units))
    }
}
