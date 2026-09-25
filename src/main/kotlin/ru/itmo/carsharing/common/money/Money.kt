package ru.itmo.carsharing.common.money

import java.math.BigDecimal
import java.math.RoundingMode

/** Деньги хранятся как numeric(12,2), округление до копейки по HALF_UP. */
object Money {
    const val SCALE: Int = 2
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE)

    fun of(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

    fun of(value: Long): BigDecimal = BigDecimal.valueOf(value).setScale(SCALE)
}
