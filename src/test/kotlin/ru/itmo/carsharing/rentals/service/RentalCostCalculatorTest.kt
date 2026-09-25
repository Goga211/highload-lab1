package ru.itmo.carsharing.rentals.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.rentals.entity.AppliedRates
import ru.itmo.carsharing.rentals.entity.OptionPriceUnit
import java.math.BigDecimal
import java.time.Instant

class RentalCostCalculatorTest {

    private val reservedAt = Instant.parse("2026-09-20T12:00:00Z")
    private val basicRates = AppliedRates(
        pricePerMinute = BigDecimal("8.00"),
        pricePerKm = BigDecimal("3.00"),
        waitingPricePerMinute = BigDecimal("2.50"),
        freeReservationMinutes = 20,
        deposit = BigDecimal("3000.00"),
    )

    private fun input(
        startedAfterMinutes: Long = 10,
        rideSeconds: Long = 45 * 60,
        km: Int = 12,
        options: List<OptionCharge> = emptyList(),
        surcharge: BigDecimal = BigDecimal.ZERO,
    ): CostInput {
        val startedAt = reservedAt.plusSeconds(startedAfterMinutes * 60)
        return CostInput(
            reservedAt = reservedAt,
            startedAt = startedAt,
            finishedAt = startedAt.plusSeconds(rideSeconds),
            startOdometerKm = 1000,
            finishOdometerKm = 1000 + km,
            rates = basicRates,
            options = options,
            zoneSurcharge = surcharge,
        )
    }

    @Test
    fun `demo trip from the report costs 636`() {
        val cost = RentalCostCalculator.calculate(
            input(
                options = listOf(
                    OptionCharge(BigDecimal("150.00"), OptionPriceUnit.PER_RENTAL, 1),
                    OptionCharge(BigDecimal("2.00"), OptionPriceUnit.PER_MINUTE, 1),
                ),
            ),
        )

        assertThat(cost.durationMinutes).isEqualTo(45)
        assertThat(cost.waitingMinutes).isZero()
        assertThat(cost.distanceKm).isEqualTo(12)
        assertThat(cost.optionTotals).containsExactly(BigDecimal("150.00"), BigDecimal("90.00"))
        assertThat(cost.total).isEqualByComparingTo("636.00")
    }

    @Test
    fun `waiting is charged only after free reservation minutes`() {
        val cost = RentalCostCalculator.calculate(input(startedAfterMinutes = 26))

        assertThat(cost.waitingMinutes).isEqualTo(6)
        assertThat(cost.waitingAmount).isEqualByComparingTo("15.00")
        assertThat(cost.total).isEqualByComparingTo(BigDecimal("360.00") + BigDecimal("36.00") + BigDecimal("15.00"))
    }

    @Test
    fun `waiting is never negative`() {
        val cost = RentalCostCalculator.calculate(input(startedAfterMinutes = 0))

        assertThat(cost.waitingMinutes).isZero()
        assertThat(cost.waitingAmount).isEqualByComparingTo("0")
    }

    @Test
    fun `every started minute is charged`() {
        val cost = RentalCostCalculator.calculate(input(rideSeconds = 45 * 60 + 1, km = 0))

        assertThat(cost.durationMinutes).isEqualTo(46)
        assertThat(cost.rideAmount).isEqualByComparingTo("368.00")
    }

    @Test
    fun `instant trip still costs one minute`() {
        val cost = RentalCostCalculator.calculate(input(rideSeconds = 0, km = 0))

        assertThat(cost.durationMinutes).isEqualTo(1)
        assertThat(cost.total).isEqualByComparingTo("8.00")
    }

    @Test
    fun `per minute option is multiplied by minutes and quantity, zone surcharge is added`() {
        val cost = RentalCostCalculator.calculate(
            input(
                rideSeconds = 10 * 60,
                km = 0,
                options = listOf(OptionCharge(BigDecimal("1.50"), OptionPriceUnit.PER_MINUTE, 2)),
                surcharge = BigDecimal("500"),
            ),
        )

        assertThat(cost.optionsAmount).isEqualByComparingTo("30.00")
        assertThat(cost.zoneSurcharge).isEqualByComparingTo("500.00")
        assertThat(cost.total).isEqualByComparingTo("610.00")
    }

    @Test
    fun `started minutes are rounded up`() {
        assertThat(RentalCostCalculator.startedMinutes(reservedAt, reservedAt)).isZero()
        assertThat(RentalCostCalculator.startedMinutes(reservedAt, reservedAt.plusMillis(1))).isEqualTo(1)
        assertThat(RentalCostCalculator.startedMinutes(reservedAt, reservedAt.plusSeconds(120))).isEqualTo(2)
        assertThat(RentalCostCalculator.startedMinutes(reservedAt, reservedAt.minusSeconds(60))).isZero()
    }
}
