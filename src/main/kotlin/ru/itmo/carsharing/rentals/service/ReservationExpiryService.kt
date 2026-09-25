package ru.itmo.carsharing.rentals.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.config.CarsharingProperties
import ru.itmo.carsharing.rentals.repository.RentalRepository
import java.time.Clock
import java.time.Duration

@Service
class ReservationExpiryService(
    private val rentals: RentalRepository,
    private val rentalService: RentalService,
    private val properties: CarsharingProperties,
    private val clock: Clock,
) {

    /**
     * Снимает пачку просроченных броней одной транзакцией. Строки выбираются FOR UPDATE SKIP LOCKED:
     * если клиент в эту же секунду стартует бронь, её строка занята, планировщик её пропустит,
     * а если планировщик успел первым, клиент получит 409.
     */
    @Transactional
    fun expireOverdueBatch(): Int {
        val cutoff = clock.instant().minus(Duration.ofMinutes(properties.rental.reservationTtlMinutes))
        val ids = rentals.lockOverdueReservations(cutoff, properties.rental.expiryBatchSize)
        ids.forEach { rentalService.expire(it) }
        return ids.size
    }
}

@Component
@ConditionalOnProperty(prefix = "carsharing.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ReservationExpiryScheduler(
    private val expiry: ReservationExpiryService,
    private val properties: CarsharingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${carsharing.rental.expiry-check-interval-ms:60000}")
    fun expireOverdueReservations() {
        var total = 0
        do {
            val expired = expiry.expireOverdueBatch()
            total += expired
        } while (expired == properties.rental.expiryBatchSize)
        if (total > 0) log.info("Expired {} overdue reservations", total)
    }
}
