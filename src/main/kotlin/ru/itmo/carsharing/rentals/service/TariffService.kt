package ru.itmo.carsharing.rentals.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.config.CarsharingProperties
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.service.FleetOperations
import ru.itmo.carsharing.rentals.dto.TariffRequest
import ru.itmo.carsharing.rentals.dto.TariffResponse
import ru.itmo.carsharing.rentals.entity.Tariff
import ru.itmo.carsharing.rentals.entity.TariffStatus
import ru.itmo.carsharing.rentals.mapper.toResponse
import ru.itmo.carsharing.rentals.repository.TariffRepository
import java.util.UUID

@Service
class TariffService(
    private val tariffs: TariffRepository,
    private val fleet: FleetOperations,
    private val properties: CarsharingProperties,
) {

    @Transactional
    fun create(request: TariffRequest): TariffResponse {
        checkFreeMinutes(request.freeReservationMinutes)
        fleet.ensureModelsExist(request.modelIds)
        val tariff = Tariff(
            name = request.name.trim(),
            pricePerMinute = Money.of(request.pricePerMinute),
            pricePerKm = Money.of(request.pricePerKm),
            waitingPricePerMinute = Money.of(request.waitingPricePerMinute),
            freeReservationMinutes = request.freeReservationMinutes,
            depositAmount = Money.of(request.depositAmount),
            validFrom = request.validFrom,
            validTo = request.validTo,
        )
        tariff.replaceModels(request.modelIds)
        return tariffs.save(tariff).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): TariffResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<TariffResponse> {
        val pageable = Paging.of(page, size, Sort.by("name"))
        return PageResponse.from(tariffs.findAll(pageable)) { it.toResponse() }
    }

    /** PUT заменяет тариф целиком, включая модели. Уже созданные аренды не меняются: ставки в них скопированы. */
    @Transactional
    fun update(id: UUID, request: TariffRequest): TariffResponse {
        val tariff = find(id)
        if (tariff.status == TariffStatus.ARCHIVED) conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Тариф в архиве")
        checkFreeMinutes(request.freeReservationMinutes)
        fleet.ensureModelsExist(request.modelIds)
        tariff.name = request.name.trim()
        tariff.pricePerMinute = Money.of(request.pricePerMinute)
        tariff.pricePerKm = Money.of(request.pricePerKm)
        tariff.waitingPricePerMinute = Money.of(request.waitingPricePerMinute)
        tariff.freeReservationMinutes = request.freeReservationMinutes
        tariff.depositAmount = Money.of(request.depositAmount)
        tariff.validFrom = request.validFrom
        tariff.validTo = request.validTo
        tariff.replaceModels(request.modelIds)
        return tariff.toResponse()
    }

    @Transactional
    fun archive(id: UUID): TariffResponse {
        val tariff = find(id)
        tariff.archive()
        return tariff.toResponse()
    }

    @Transactional(readOnly = true)
    fun models(id: UUID, page: Int, size: Int): PageResponse<VehicleModelResponse> =
        fleet.findModels(find(id).modelIds.toSet(), page, size)

    @Transactional
    fun replaceModels(id: UUID, modelIds: Set<UUID>): TariffResponse {
        val tariff = find(id)
        fleet.ensureModelsExist(modelIds)
        tariff.replaceModels(modelIds)
        return tariff.toResponse()
    }

    fun find(id: UUID): Tariff = tariffs.findWithModelsById(id) ?: throw NotFoundException("Тариф", id)

    /** Если бесплатное ожидание не короче жизни брони, платное ожидание никогда не начнётся. */
    private fun checkFreeMinutes(freeMinutes: Int) {
        val ttl = properties.rental.reservationTtlMinutes
        if (freeMinutes >= ttl) {
            unprocessable(
                ErrorCode.BUSINESS_RULE_VIOLATION,
                "Бесплатных минут брони ($freeMinutes) должно быть меньше, чем живёт бронь ($ttl мин)",
            )
        }
    }
}
