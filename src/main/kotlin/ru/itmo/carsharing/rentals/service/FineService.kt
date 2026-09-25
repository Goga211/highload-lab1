package ru.itmo.carsharing.rentals.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.billing.service.BillingOperations
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.service.FleetOperations
import ru.itmo.carsharing.rentals.dto.FineRequest
import ru.itmo.carsharing.rentals.dto.FineResponse
import ru.itmo.carsharing.rentals.entity.FineStatus
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.entity.TrafficFine
import ru.itmo.carsharing.rentals.mapper.toResponse
import ru.itmo.carsharing.rentals.repository.RentalRepository
import ru.itmo.carsharing.rentals.repository.TrafficFineRepository
import java.util.UUID

/** Штрафы лежат в домене аренд, потому что перевыставление ищет аренду по машине и времени. */
@Service
class FineService(
    private val fines: TrafficFineRepository,
    private val rentals: RentalRepository,
    private val fleet: FleetOperations,
    private val billing: BillingOperations,
) {

    @Transactional
    fun create(request: FineRequest): FineResponse {
        fleet.ensureVehicleExists(request.vehicleId)
        if (fines.existsByResolutionNumber(request.resolutionNumber)) {
            conflict(ErrorCode.DUPLICATE_RESOURCE, "Постановление ${request.resolutionNumber} уже введено")
        }
        val fine = TrafficFine(
            vehicleId = request.vehicleId,
            resolutionNumber = request.resolutionNumber,
            violatedAt = request.violatedAt,
            amount = Money.of(request.amount),
        )
        return fines.save(fine).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): FineResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(status: FineStatus?, page: Int, size: Int): PageResponse<FineResponse> {
        val pageable = Paging.of(page, size, Sort.by("violatedAt").descending())
        val result = if (status == null) fines.findAll(pageable) else fines.findAllByStatus(status, pageable)
        return PageResponse.from(result) { it.toResponse() }
    }

    /**
     * Транзакция 4. Перевыставление. Без транзакции штраф можно списать дважды
     * или списать без смены статуса. Строка штрафа блокируется, платёж идемпотентен по ключу.
     */
    @Transactional
    fun rebill(id: UUID): FineResponse {
        val fine = lock(id)
        if (fine.status != FineStatus.RECEIVED) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Перевыставить можно только новый штраф, статус ${fine.status}")
        }
        val rental = rentals.findCovering(
            fine.vehicleId,
            fine.violatedAt,
            listOf(RentalStatus.ACTIVE, RentalStatus.COMPLETED),
        ).firstOrNull()
        if (rental == null) {
            fine.markNoRental()
        } else {
            val paymentId = billing.chargeFine(rental.userId, rental.id, fine.id, fine.amount)
            fine.rebill(rental, paymentId)
        }
        return fine.toResponse()
    }

    @Transactional
    fun dispute(id: UUID, reason: String): FineResponse {
        val fine = lock(id)
        fine.dispute(reason.trim())
        return fine.toResponse()
    }

    /** Обжалование удовлетворено: штраф отменяется, уже списанная сумма возвращается клиенту. */
    @Transactional
    fun cancel(id: UUID): FineResponse {
        val fine = lock(id)
        fine.cancel()
        val rental = fine.rental
        val paymentId = fine.paymentId
        if (rental != null && paymentId != null) {
            billing.refundFine(rental.userId, rental.id, fine.id, fine.amount)
        }
        return fine.toResponse()
    }

    private fun find(id: UUID): TrafficFine = fines.findByIdOrNull(id) ?: throw NotFoundException("Штраф", id)

    private fun lock(id: UUID): TrafficFine = fines.findByIdForUpdate(id) ?: throw NotFoundException("Штраф", id)
}
