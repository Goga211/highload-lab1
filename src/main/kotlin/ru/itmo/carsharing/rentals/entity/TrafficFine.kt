package ru.itmo.carsharing.rentals.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.common.error.invalidTransition
import ru.itmo.carsharing.rentals.service.FineRules
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Штраф ГИБДД. Постановление приходит на компанию, система ищет аренду по машине
 * и времени нарушения и перевыставляет сумму клиенту.
 */
@Entity
@Table(name = "traffic_fine")
class TrafficFine(
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    var vehicleId: UUID,

    @field:Pattern(regexp = FineRules.RESOLUTION_NUMBER_REGEX)
    @Column(name = "resolution_number", nullable = false, length = 25, updatable = false)
    var resolutionNumber: String,

    @Column(name = "violated_at", nullable = false, updatable = false)
    var violatedAt: Instant,

    @field:Positive
    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    var amount: BigDecimal,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: FineStatus = FineStatus.RECEIVED
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id")
    var rental: Rental? = null
        protected set

    /** Платёж FINE_CHARGE в домене billing. */
    @Column(name = "payment_id")
    var paymentId: UUID? = null
        protected set

    @field:Size(max = 500)
    @Column(name = "dispute_reason", length = 500)
    var disputeReason: String? = null
        protected set

    fun rebill(rental: Rental, paymentId: UUID) {
        moveTo(FineStatus.REBILLED)
        this.rental = rental
        this.paymentId = paymentId
    }

    fun markNoRental() {
        moveTo(FineStatus.NO_RENTAL)
    }

    fun dispute(reason: String) {
        moveTo(FineStatus.DISPUTED)
        disputeReason = reason
    }

    /** Обжалование отклонено: штраф снова на клиенте, если он был перевыставлен, иначе на компании. */
    fun rejectDispute() {
        if (status != FineStatus.DISPUTED) invalidTransition("Штраф", status, FineStatus.REBILLED)
        moveTo(if (rental != null) FineStatus.REBILLED else FineStatus.NO_RENTAL)
    }

    fun cancel() {
        moveTo(FineStatus.CANCELLED)
    }

    private fun moveTo(target: FineStatus) {
        if (!status.canTransitionTo(target)) invalidTransition("Штраф", status, target)
        status = target
    }
}
