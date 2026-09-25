package ru.itmo.carsharing.fleet.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.VehicleModelRequest
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.entity.VehicleModel
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.SparePartRepository
import ru.itmo.carsharing.fleet.repository.VehicleModelRepository
import ru.itmo.carsharing.fleet.repository.VehicleRepository
import java.util.UUID

@Service
class VehicleModelService(
    private val models: VehicleModelRepository,
    private val vehicles: VehicleRepository,
    private val spareParts: SparePartRepository,
) {

    @Transactional
    fun create(request: VehicleModelRequest): VehicleModelResponse {
        val brand = request.brand.trim()
        val name = request.model.trim()
        if (models.existsByBrandAndModel(brand, name)) duplicate(brand, name)
        val model = VehicleModel(
            brand = brand,
            model = name,
            vehicleClass = request.vehicleClass,
            fuelType = request.fuelType,
            seats = request.seats,
            serviceIntervalKm = request.serviceIntervalKm,
        )
        return models.save(model).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): VehicleModelResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<VehicleModelResponse> {
        val pageable = Paging.of(page, size, Sort.by("brand", "model"))
        return PageResponse.from(models.findAll(pageable)) { it.toResponse() }
    }

    @Transactional
    fun update(id: UUID, request: VehicleModelRequest): VehicleModelResponse {
        val model = find(id)
        val brand = request.brand.trim()
        val name = request.model.trim()
        if (models.existsByBrandAndModelAndIdNot(brand, name, id)) duplicate(brand, name)
        model.brand = brand
        model.model = name
        model.vehicleClass = request.vehicleClass
        model.fuelType = request.fuelType
        model.seats = request.seats
        model.serviceIntervalKm = request.serviceIntervalKm
        return model.toResponse()
    }

    /** Модель удаляется физически, только если на неё нет ссылок: машин, запчастей, тарифов. */
    @Transactional
    fun delete(id: UUID) {
        val model = find(id)
        if (vehicles.existsByModelId(id)) conflict(ErrorCode.RESOURCE_IN_USE, "У модели есть машины, удалить нельзя")
        if (spareParts.existsCompatibleWithModel(id)) {
            conflict(ErrorCode.RESOURCE_IN_USE, "Модель указана в совместимости запчастей, удалить нельзя")
        }
        models.delete(model)
        models.flush()
    }

    fun find(id: UUID): VehicleModel = models.findByIdOrNull(id) ?: throw NotFoundException("Модель", id)

    private fun duplicate(brand: String, model: String): Nothing =
        conflict(ErrorCode.DUPLICATE_RESOURCE, "Модель $brand $model уже есть в справочнике")
}
