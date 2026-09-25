package ru.itmo.carsharing.fleet.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.fleet.entity.FuelType
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.fleet.entity.VehicleStatus
import ru.itmo.carsharing.fleet.entity.ZoneType
import ru.itmo.carsharing.fleet.service.VehicleRules
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private const val MAX_SERVICE_INTERVAL_KM: Long = 100_000
private const val MAX_ODOMETER_KM: Long = 2_000_000
private const val MAX_ZONE_RADIUS_M: Long = 100_000

data class VehicleModelRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val brand: String,

    @field:NotBlank
    @field:Size(max = 64)
    val model: String,

    val vehicleClass: VehicleClass,
    val fuelType: FuelType,

    @field:Min(1)
    @field:Max(9)
    val seats: Int,

    @field:Positive
    @field:Max(MAX_SERVICE_INTERVAL_KM)
    val serviceIntervalKm: Int,
)

data class VehicleModelResponse(
    val id: UUID,
    val brand: String,
    val model: String,
    val vehicleClass: VehicleClass,
    val fuelType: FuelType,
    val seats: Int,
    val serviceIntervalKm: Int,
)

data class ParkingZoneRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    val zoneType: ZoneType,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val centerLatitude: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val centerLongitude: Double,

    @field:Positive
    @field:Max(MAX_ZONE_RADIUS_M)
    val radiusM: Int,

    val finishAllowed: Boolean,

    @field:PositiveOrZero
    @field:Digits(integer = 10, fraction = 2)
    val finishSurcharge: BigDecimal,

    val active: Boolean = true,
)

data class ParkingZoneResponse(
    val id: UUID,
    val name: String,
    val zoneType: ZoneType,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusM: Int,
    val finishAllowed: Boolean,
    val finishSurcharge: BigDecimal,
    val active: Boolean,
)

data class CreateVehicleRequest(
    @field:Pattern(regexp = VehicleRules.VIN_REGEX, message = "VIN: 17 символов, латинские буквы без I, O, Q и цифры")
    val vin: String,

    @field:Pattern(regexp = VehicleRules.PLATE_REGEX, message = "госномер в формате А123ВС777")
    val plateNumber: String,

    val modelId: UUID,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double,

    @field:PositiveOrZero
    @field:Max(MAX_ODOMETER_KM)
    val odometerKm: Int,

    @field:Min(0)
    @field:Max(100)
    val fuelLevelPercent: Int,
)

data class UpdateVehicleRequest(
    @field:Pattern(regexp = VehicleRules.PLATE_REGEX, message = "госномер в формате А123ВС777")
    val plateNumber: String,

    val modelId: UUID,
)

/** Данные бортового блока. Клиент их не передаёт и не может занизить пробег. */
data class TelemetryRequest(
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double,

    @field:PositiveOrZero
    @field:Max(MAX_ODOMETER_KM)
    val odometerKm: Int,

    @field:Min(0)
    @field:Max(100)
    val fuelLevelPercent: Int,
)

data class VehicleResponse(
    val id: UUID,
    val vin: String,
    val plateNumber: String,
    val model: VehicleModelResponse,
    val status: VehicleStatus,
    val odometerKm: Int,
    val fuelLevelPercent: Int,
    val latitude: Double,
    val longitude: Double,
    val currentZoneId: UUID?,
    val currentZoneName: String?,
    val lastServiceOdometerKm: Int,
    val telemetryUpdatedAt: Instant,
    val version: Long,
)

data class NearbyVehicleResponse(val distanceM: Int, val vehicle: VehicleResponse)
