package ru.itmo.carsharing.rentals.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.common.error.invalidTransition
import ru.itmo.carsharing.rentals.service.RentalCost
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Ставки тарифа, скопированные в аренду при брони. */
@Embeddable
class AppliedRates(
    @Column(name = "applied_price_per_minute", nullable = false, precision = 12, scale = 2, updatable = false)
    var pricePerMinute: BigDecimal,

    @Column(name = "applied_price_per_km", nullable = false, precision = 12, scale = 2, updatable = false)
    var pricePerKm: BigDecimal,

    @Column(name = "applied_waiting_price_per_minute", nullable = false, precision = 12, scale = 2, updatable = false)
    var waitingPricePerMinute: BigDecimal,

    @Column(name = "applied_free_reservation_minutes", nullable = false, updatable = false)
    var freeReservationMinutes: Int,

    @Column(name = "applied_deposit", nullable = false, precision = 12, scale = 2, updatable = false)
    var deposit: BigDecimal,
)

/**
 * Аренда от брони до завершения. Ссылки на пользователя, машину и зоны ведут в другие домены
 * и хранятся как uuid: в ЛР2 это разные сервисы с разными базами.
 */
@Entity
@Table(name = "rental")
class Rental(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    var vehicleId: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_id", nullable = false, updatable = false)
    var tariff: Tariff,

    @Embedded
    var rates: AppliedRates,

    @Column(name = "reserved_at", nullable = false, updatable = false)
    var reservedAt: Instant,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: RentalStatus = RentalStatus.RESERVED
        protected set

    @Column(name = "started_at")
    var startedAt: Instant? = null
        protected set

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
        protected set

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null
        protected set

    @field:Size(max = 500)
    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = null
        protected set

    @Column(name = "start_zone_id")
    var startZoneId: UUID? = null
        protected set

    @Column(name = "finish_zone_id")
    var finishZoneId: UUID? = null
        protected set

    @Column(name = "start_odometer_km")
    var startOdometerKm: Int? = null
        protected set

    @Column(name = "finish_odometer_km")
    var finishOdometerKm: Int? = null
        protected set

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null
        protected set

    @Column(name = "waiting_minutes")
    var waitingMinutes: Int? = null
        protected set

    @Column(name = "distance_km")
    var distanceKm: Int? = null
        protected set

    @Column(name = "options_amount", precision = 12, scale = 2)
    var optionsAmount: BigDecimal? = null
        protected set

    @Column(name = "zone_surcharge", precision = 12, scale = 2)
    var zoneSurcharge: BigDecimal? = null
        protected set

    @Column(name = "total_amount", precision = 12, scale = 2)
    var totalAmount: BigDecimal? = null
        protected set

    @OneToMany(mappedBy = "rental", cascade = [CascadeType.ALL], orphanRemoval = true)
    var options: MutableList<RentalOptionItem> = mutableListOf()
        protected set

    fun addOption(option: RentalOption, quantity: Int) {
        options.add(RentalOptionItem(this, option, quantity, option.price, option.priceUnit))
    }

    fun start(at: Instant, odometerKm: Int, zoneId: UUID?) {
        moveTo(RentalStatus.ACTIVE)
        startedAt = at
        startOdometerKm = odometerKm
        startZoneId = zoneId
    }

    fun complete(at: Instant, odometerKm: Int, zoneId: UUID, cost: RentalCost) {
        moveTo(RentalStatus.COMPLETED)
        finishedAt = at
        finishOdometerKm = odometerKm
        finishZoneId = zoneId
        durationMinutes = cost.durationMinutes
        waitingMinutes = cost.waitingMinutes
        distanceKm = cost.distanceKm
        optionsAmount = cost.optionsAmount
        zoneSurcharge = cost.zoneSurcharge
        totalAmount = cost.total
        options.zip(cost.optionTotals).forEach { (item, total) -> item.charge(total) }
    }

    fun cancel(at: Instant, reason: String) {
        moveTo(RentalStatus.CANCELLED)
        cancelledAt = at
        cancelReason = reason
    }

    fun expire(at: Instant) {
        moveTo(RentalStatus.EXPIRED)
        cancelledAt = at
        cancelReason = "Бронь истекла"
    }

    private fun moveTo(target: RentalStatus) {
        if (!status.canTransitionTo(target)) invalidTransition("Аренда", status, target)
        status = target
    }
}
