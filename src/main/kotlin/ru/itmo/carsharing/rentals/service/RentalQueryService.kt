package ru.itmo.carsharing.rentals.service

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.SliceResponse
import ru.itmo.carsharing.rentals.dto.RentalResponse
import ru.itmo.carsharing.rentals.entity.Rental
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.mapper.toResponse
import ru.itmo.carsharing.rentals.repository.RentalRepository
import ru.itmo.carsharing.users.service.UserDirectory
import java.util.UUID

@Service
class RentalQueryService(private val rentals: RentalRepository, private val users: UserDirectory) {

    @Transactional(readOnly = true)
    fun get(id: UUID): RentalResponse =
        (rentals.findWithOptionsById(id) ?: throw NotFoundException("Аренда", id)).toResponse()

    @Transactional(readOnly = true)
    fun list(status: RentalStatus?, userId: UUID?, page: Int, size: Int): PageResponse<RentalResponse> {
        val pageable = Paging.of(page, size, Sort.by("reservedAt").descending())
        return PageResponse.from(rentals.findAll(filter(status, userId), pageable)) {
            it.toResponse(withOptions = false)
        }
    }

    /**
     * История поездок клиента для бесконечной прокрутки в приложении.
     * Общее количество не нужно и считать его дорого, поэтому Slice без count-запроса.
     */
    @Transactional(readOnly = true)
    fun history(userId: UUID, page: Int, size: Int): SliceResponse<RentalResponse> {
        users.ensureExists(userId)
        val pageable = Paging.of(page, size, Sort.by("reservedAt").descending())
        return SliceResponse.from(rentals.findAllByUserId(userId, pageable)) { it.toResponse(withOptions = false) }
    }

    private fun filter(status: RentalStatus?, userId: UUID?): Specification<Rental> = Specification { root, _, cb ->
        val predicates = buildList<Predicate> {
            status?.let { add(cb.equal(root.get<RentalStatus>("status"), it)) }
            userId?.let { add(cb.equal(root.get<UUID>("userId"), it)) }
        }
        cb.and(*predicates.toTypedArray())
    }
}
