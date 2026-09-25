package ru.itmo.carsharing.fleet.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.SparePartRequest
import ru.itmo.carsharing.fleet.dto.SparePartResponse
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.entity.SparePart
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.MaintenancePartRepository
import ru.itmo.carsharing.fleet.repository.SparePartRepository
import ru.itmo.carsharing.fleet.repository.VehicleModelRepository
import java.util.UUID

@Service
class SparePartService(
    private val spareParts: SparePartRepository,
    private val maintenanceParts: MaintenancePartRepository,
    private val models: VehicleModelRepository,
) {

    @Transactional
    fun create(request: SparePartRequest): SparePartResponse {
        if (spareParts.existsByArticle(request.article)) duplicate(request.article)
        val part = SparePart(
            article = request.article,
            name = request.name.trim(),
            stockQuantity = request.stockQuantity,
            price = Money.of(request.price),
        )
        return spareParts.save(part).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): SparePartResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<SparePartResponse> {
        val pageable = Paging.of(page, size, Sort.by("article"))
        return PageResponse.from(spareParts.findAll(pageable)) { it.toResponse() }
    }

    /** Правка карточки защищена version: параллельное списание со склада даст 409, а не потерю остатка. */
    @Transactional
    fun update(id: UUID, request: SparePartRequest): SparePartResponse {
        val part = find(id)
        if (spareParts.existsByArticleAndIdNot(request.article, id)) duplicate(request.article)
        part.update(request.article, request.name.trim(), request.stockQuantity, Money.of(request.price))
        return spareParts.saveAndFlush(part).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val part = find(id)
        if (maintenanceParts.existsByPartId(id)) {
            conflict(ErrorCode.RESOURCE_IN_USE, "Запчасть уже списывалась в наряды, удалить нельзя")
        }
        spareParts.delete(part)
    }

    @Transactional(readOnly = true)
    fun compatibleModels(id: UUID, page: Int, size: Int): PageResponse<VehicleModelResponse> {
        find(id)
        return PageResponse.from(spareParts.findCompatibleModels(id, Paging.of(page, size))) { it.toResponse() }
    }

    @Transactional
    fun replaceCompatibleModels(id: UUID, modelIds: Set<UUID>): PageResponse<VehicleModelResponse> {
        val part = find(id)
        val found = models.findAllById(modelIds)
        val missing = modelIds - found.map { it.id }.toSet()
        if (missing.isNotEmpty()) throw NotFoundException("Модель", missing.first())
        part.replaceCompatibleModels(found)
        spareParts.flush()
        return compatibleModels(id, 0, Paging.MAX_PAGE_SIZE.toInt())
    }

    private fun find(id: UUID): SparePart = spareParts.findByIdOrNull(id) ?: throw NotFoundException("Запчасть", id)

    private fun duplicate(article: String): Nothing =
        conflict(ErrorCode.DUPLICATE_RESOURCE, "Запчасть с артикулом $article уже есть")
}
