package ru.itmo.carsharing.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("carsharing")
data class CarsharingProperties(val rental: Rental = Rental(), val fleet: Fleet = Fleet()) {
    data class Rental(
        /** Сколько живёт бронь до старта. Должно быть больше бесплатных минут тарифа. */
        val reservationTtlMinutes: Long = 30,
        val expiryBatchSize: Int = 100,
        val expiryCheckIntervalMs: Long = 60_000,
    )

    data class Fleet(
        /** Ниже этого уровня топлива после поездки создаётся наряд на заправку. */
        val fuelLowThresholdPercent: Int = 15,
    )
}
