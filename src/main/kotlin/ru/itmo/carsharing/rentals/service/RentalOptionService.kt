package ru.itmo.carsharing.rentals.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.rentals.dto.RentalOptionRequest
import ru.itmo.carsharing.rentals.dto.RentalOptionResponse
import ru.itmo.carsharing.rentals.entity.RentalOption
import ru.itmo.carsharing.rentals.mapper.toResponse
import ru.itmo.carsharing.rentals.repository.RentalOptionRepository
import java.util.UUID

@Service
class RentalOptionService(private val options: RentalOptionRepository) {

    @Transactional
    fun create(request: RentalOptionRequest): RentalOptionResponse {
        if (options.existsByCode(request.code)) {
            conflict(ErrorCode.DUPLICATE_RESOURCE, "Опция с кодом ${request.code} уже есть")
        }
        val option = RentalOption(
            code = request.code,
            name = request.name.trim(),
            price = Money.of(request.price),
            priceUnit = request.priceUnit,
        )
        if (!request.active) option.deactivate()
        return options.save(option).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): RentalOptionResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<RentalOptionResponse> {
        val pageable = Paging.of(page, size, Sort.by("code"))
        return PageResponse.from(options.findAll(pageable)) { it.toResponse() }
    }

    /** Код опции неизменяем: на него ориентируются клиенты API. Цена в прошлых арендах не меняется. */
    @Transactional
    fun update(id: UUID, request: RentalOptionRequest): RentalOptionResponse {
        val option = find(id)
        if (option.code != request.code) {
            unprocessable(ErrorCode.BUSINESS_RULE_VIOLATION, "Код опции ${option.code} менять нельзя")
        }
        option.name = request.name.trim()
        option.price = Money.of(request.price)
        option.priceUnit = request.priceUnit
        if (request.active) option.activate() else option.deactivate()
        return option.toResponse()
    }

    /** На опции ссылаются аренды, поэтому удаление это деактивация. */
    @Transactional
    fun deactivate(id: UUID) {
        find(id).deactivate()
    }

    fun find(id: UUID): RentalOption = options.findByIdOrNull(id) ?: throw NotFoundException("Опция", id)
}
