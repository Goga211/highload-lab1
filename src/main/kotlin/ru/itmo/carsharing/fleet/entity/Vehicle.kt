package ru.itmo.carsharing.fleet.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import org.hibernate.annotations.DynamicUpdate
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.fleet.service.VehicleRules
import java.time.Instant

/**
 * Машина. Статус и телеметрия меняются только точечными UPDATE в репозитории, каждый из них
 * увеличивает version. Через JPA сохраняются лишь карточные поля (госномер, модель), и
 * @DynamicUpdate пишет только изменённые колонки, поэтому сохранение карточки не затрёт
 * ни бронь, ни свежую телеметрию, а проверка version вернёт 409 при гонке.
 */
@Entity
@Table(name = "vehicle")
@DynamicUpdate
class Vehicle(
    @field:Pattern(regexp = VehicleRules.VIN_REGEX)
    @Column(name = "vin", nullable = false, length = 17, updatable = false)
    var vin: String,

    @field:Pattern(regexp = VehicleRules.PLATE_REGEX)
    @Column(name = "plate_number", nullable = false, length = 9)
    var plateNumber: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    var model: VehicleModel,

    @field:PositiveOrZero
    @Column(name = "odometer_km", nullable = false)
    var odometerKm: Int,

    @field:Min(0)
    @field:Max(100)
    @Column(name = "fuel_level_percent", nullable = false)
    var fuelLevelPercent: Int,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    @Column(name = "latitude", nullable = false)
    var latitude: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    @Column(name = "longitude", nullable = false)
    var longitude: Double,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_zone_id")
    var currentZone: ParkingZone?,

    @Column(name = "telemetry_updated_at", nullable = false)
    var telemetryUpdatedAt: Instant,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: VehicleStatus = VehicleStatus.AVAILABLE
        protected set

    @field:PositiveOrZero
    @Column(name = "last_service_odometer_km", nullable = false)
    var lastServiceOdometerKm: Int = odometerKm
        protected set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    fun updateCard(plateNumber: String, model: VehicleModel) {
        this.plateNumber = plateNumber
        this.model = model
    }
}
