package ru.itmo.carsharing.fleet.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import java.math.BigDecimal

/**
 * Зона это круг: центр и радиус. HOME покрывает весь город, остальные накладываются сверху,
 * при пересечении побеждает меньший радиус.
 */
@Entity
@Table(name = "parking_zone")
class ParkingZone(
    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 32)
    var zoneType: ZoneType,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    @Column(name = "center_latitude", nullable = false)
    var centerLatitude: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    @Column(name = "center_longitude", nullable = false)
    var centerLongitude: Double,

    @field:Positive
    @Column(name = "radius_m", nullable = false)
    var radiusM: Int,

    @Column(name = "is_finish_allowed", nullable = false)
    var finishAllowed: Boolean,

    @field:PositiveOrZero
    @Column(name = "finish_surcharge", nullable = false, precision = 12, scale = 2)
    var finishSurcharge: BigDecimal,
) : BaseEntity() {

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true
        protected set

    fun deactivate() {
        active = false
    }

    fun activate() {
        active = true
    }
}
