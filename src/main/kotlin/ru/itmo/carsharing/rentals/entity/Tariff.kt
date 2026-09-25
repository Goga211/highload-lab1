package ru.itmo.carsharing.rentals.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.hibernate.annotations.BatchSize
import ru.itmo.carsharing.common.entity.BaseEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Прайс. Ставки копируются в аренду при бронировании, поэтому правка тарифа не меняет
 * стоимость уже начатых и закрытых поездок.
 */
@Entity
@Table(name = "tariff")
class Tariff(
    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @field:Positive
    @Column(name = "price_per_minute", nullable = false, precision = 12, scale = 2)
    var pricePerMinute: BigDecimal,

    @field:Positive
    @Column(name = "price_per_km", nullable = false, precision = 12, scale = 2)
    var pricePerKm: BigDecimal,

    @field:Positive
    @Column(name = "waiting_price_per_minute", nullable = false, precision = 12, scale = 2)
    var waitingPricePerMinute: BigDecimal,

    @field:Min(0)
    @field:Max(120)
    @Column(name = "free_reservation_minutes", nullable = false)
    var freeReservationMinutes: Int,

    @field:Positive
    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    var depositAmount: BigDecimal,

    @Column(name = "valid_from", nullable = false)
    var validFrom: Instant,

    @Column(name = "valid_to")
    var validTo: Instant?,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: TariffStatus = TariffStatus.ACTIVE
        protected set

    /**
     * Модели, к которым применим тариф. Таблица tariff_vehicle_model это связь многие-ко-многим,
     * но модели лежат в домене fleet, поэтому со стороны тарифа храним только их идентификаторы.
     */
    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "tariff_vehicle_model", joinColumns = [JoinColumn(name = "tariff_id")])
    @Column(name = "model_id", nullable = false)
    var modelIds: MutableSet<UUID> = mutableSetOf()
        protected set

    fun isApplicable(modelId: UUID, at: Instant): Boolean = status == TariffStatus.ACTIVE &&
        !validFrom.isAfter(at) &&
        (validTo == null || validTo!!.isAfter(at)) &&
        modelId in modelIds

    fun archive() {
        status = TariffStatus.ARCHIVED
    }

    fun replaceModels(ids: Collection<UUID>) {
        modelIds.clear()
        modelIds.addAll(ids)
    }
}
