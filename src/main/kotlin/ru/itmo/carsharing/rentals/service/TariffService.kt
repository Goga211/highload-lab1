package ru.itmo.carsharing.rentals.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
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
) {

    @Transactional
    fun create(request: TariffRequest): TariffResponse {
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

    /** Правка прайса не трогает уже созданные аренды: ставки в них скопированы при брони. */
    @Transactional
    fun update(id: UUID, request: TariffRequest): TariffResponse {
        val tariff = find(id)
        if (tariff.status == TariffStatus.ARCHIVED) conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Тариф в архиве")
        tariff.name = request.name.trim()
        tariff.pricePerMinute = Money.of(request.pricePerMinute)
        tariff.pricePerKm = Money.of(request.pricePerKm)
        tariff.waitingPricePerMinute = Money.of(request.waitingPricePerMinute)
        tariff.freeReservationMinutes = request.freeReservationMinutes
        tariff.depositAmount = Money.of(request.depositAmount)
        tariff.validFrom = request.validFrom
        tariff.validTo = request.validTo
        if (request.modelIds.isNotEmpty()) {
            fleet.ensureModelsExist(request.modelIds)
            tariff.replaceModels(request.modelIds)
        }
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
}
