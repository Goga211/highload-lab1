package ru.itmo.carsharing.rentals.mapper

import ru.itmo.carsharing.rentals.dto.AppliedRatesResponse
import ru.itmo.carsharing.rentals.dto.FineResponse
import ru.itmo.carsharing.rentals.dto.RentalCostResponse
import ru.itmo.carsharing.rentals.dto.RentalOptionItemResponse
import ru.itmo.carsharing.rentals.dto.RentalOptionResponse
import ru.itmo.carsharing.rentals.dto.RentalResponse
import ru.itmo.carsharing.rentals.dto.TariffResponse
import ru.itmo.carsharing.rentals.entity.Rental
import ru.itmo.carsharing.rentals.entity.RentalOption
import ru.itmo.carsharing.rentals.entity.RentalOptionItem
import ru.itmo.carsharing.rentals.entity.Tariff
import ru.itmo.carsharing.rentals.entity.TrafficFine

fun Tariff.toResponse(): TariffResponse = TariffResponse(
    id = id,
    name = name,
    pricePerMinute = pricePerMinute,
    pricePerKm = pricePerKm,
    waitingPricePerMinute = waitingPricePerMinute,
    freeReservationMinutes = freeReservationMinutes,
    depositAmount = depositAmount,
    validFrom = validFrom,
    validTo = validTo,
    status = status,
    modelIds = modelIds.toSet(),
)

fun RentalOption.toResponse(): RentalOptionResponse = RentalOptionResponse(
    id = id,
    code = code,
    name = name,
    price = price,
    priceUnit = priceUnit,
    active = active,
)

fun RentalOptionItem.toResponse(): RentalOptionItemResponse = RentalOptionItemResponse(
    optionId = option.id,
    code = option.code,
    name = option.name,
    quantity = quantity,
    unitPrice = unitPriceSnapshot,
    priceUnit = priceUnitSnapshot,
    totalAmount = totalAmount,
)

/** withOptions = false для списков: опции не подгружаются, чтобы не было N+1. */
fun Rental.toResponse(withOptions: Boolean = true): RentalResponse = RentalResponse(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    tariffId = tariff.id,
    status = status,
    reservedAt = reservedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    cancelledAt = cancelledAt,
    cancelReason = cancelReason,
    startZoneId = startZoneId,
    finishZoneId = finishZoneId,
    startOdometerKm = startOdometerKm,
    finishOdometerKm = finishOdometerKm,
    rates = AppliedRatesResponse(
        pricePerMinute = rates.pricePerMinute,
        pricePerKm = rates.pricePerKm,
        waitingPricePerMinute = rates.waitingPricePerMinute,
        freeReservationMinutes = rates.freeReservationMinutes,
        deposit = rates.deposit,
    ),
    cost = RentalCostResponse(
        durationMinutes = durationMinutes,
        waitingMinutes = waitingMinutes,
        distanceKm = distanceKm,
        optionsAmount = optionsAmount,
        zoneSurcharge = zoneSurcharge,
        totalAmount = totalAmount,
    ),
    options = if (withOptions) options.map { it.toResponse() } else null,
)

fun TrafficFine.toResponse(): FineResponse = FineResponse(
    id = id,
    vehicleId = vehicleId,
    rentalId = rental?.id,
    paymentId = paymentId,
    resolutionNumber = resolutionNumber,
    violatedAt = violatedAt,
    amount = amount,
    status = status,
    disputeReason = disputeReason,
    createdAt = createdAt,
)
