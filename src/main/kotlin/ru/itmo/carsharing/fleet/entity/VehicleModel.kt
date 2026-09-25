package ru.itmo.carsharing.fleet.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity

@Entity
@Table(name = "vehicle_model")
class VehicleModel(
    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "brand", nullable = false, length = 64)
    var brand: String,

    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "model", nullable = false, length = 64)
    var model: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_class", nullable = false, length = 32)
    var vehicleClass: VehicleClass,

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 32)
    var fuelType: FuelType,

    @field:Min(1)
    @field:Max(9)
    @Column(name = "seats", nullable = false)
    var seats: Int,

    /** Пробег между плановыми ТО. Превышение после поездки создаёт наряд SCHEDULED_SERVICE. */
    @field:Positive
    @Column(name = "service_interval_km", nullable = false)
    var serviceIntervalKm: Int,
) : BaseEntity()
