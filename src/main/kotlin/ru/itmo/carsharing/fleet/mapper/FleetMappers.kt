package ru.itmo.carsharing.fleet.mapper

import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.fleet.dto.MaintenancePartResponse
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskDetailsResponse
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskResponse
import ru.itmo.carsharing.fleet.dto.ParkingZoneResponse
import ru.itmo.carsharing.fleet.dto.SparePartResponse
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.dto.VehicleResponse
import ru.itmo.carsharing.fleet.entity.MaintenancePart
import ru.itmo.carsharing.fleet.entity.MaintenanceTask
import ru.itmo.carsharing.fleet.entity.ParkingZone
import ru.itmo.carsharing.fleet.entity.SparePart
import ru.itmo.carsharing.fleet.entity.Vehicle
import ru.itmo.carsharing.fleet.entity.VehicleModel
import java.math.BigDecimal

fun VehicleModel.toResponse(): VehicleModelResponse = VehicleModelResponse(
    id = id,
    brand = brand,
    model = model,
    vehicleClass = vehicleClass,
    fuelType = fuelType,
    seats = seats,
    serviceIntervalKm = serviceIntervalKm,
)

fun ParkingZone.toResponse(): ParkingZoneResponse = ParkingZoneResponse(
    id = id,
    name = name,
    zoneType = zoneType,
    centerLatitude = centerLatitude,
    centerLongitude = centerLongitude,
    radiusM = radiusM,
    finishAllowed = finishAllowed,
    finishSurcharge = finishSurcharge,
    active = active,
)

fun Vehicle.toResponse(): VehicleResponse = VehicleResponse(
    id = id,
    vin = vin,
    plateNumber = plateNumber,
    model = model.toResponse(),
    status = status,
    odometerKm = odometerKm,
    fuelLevelPercent = fuelLevelPercent,
    latitude = latitude,
    longitude = longitude,
    currentZoneId = currentZone?.id,
    currentZoneName = currentZone?.name,
    lastServiceOdometerKm = lastServiceOdometerKm,
    telemetryUpdatedAt = telemetryUpdatedAt,
    version = version,
)

fun MaintenanceTask.toResponse(): MaintenanceTaskResponse = MaintenanceTaskResponse(
    id = id,
    vehicleId = vehicle.id,
    taskType = taskType,
    status = status,
    description = description,
    assignedTo = assignedTo,
    openedAt = openedAt,
    closedAt = closedAt,
    odometerAtOpenKm = odometerAtOpenKm,
)

fun MaintenancePart.toResponse(): MaintenancePartResponse = MaintenancePartResponse(
    partId = part.id,
    article = part.article,
    name = part.name,
    quantity = quantity,
    unitPriceSnapshot = unitPriceSnapshot,
    totalAmount = Money.of(unitPriceSnapshot * BigDecimal(quantity)),
)

fun MaintenanceTask.toDetailsResponse(): MaintenanceTaskDetailsResponse {
    val partResponses = parts.map { it.toResponse() }
    return MaintenanceTaskDetailsResponse(
        task = toResponse(),
        parts = partResponses,
        partsTotal = partResponses.fold(Money.ZERO) { acc, part -> acc + part.totalAmount },
    )
}

fun SparePart.toResponse(): SparePartResponse = SparePartResponse(
    id = id,
    article = article,
    name = name,
    stockQuantity = stockQuantity,
    price = price,
    version = version,
)
