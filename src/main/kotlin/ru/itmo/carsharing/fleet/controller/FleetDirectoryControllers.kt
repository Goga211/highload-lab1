package ru.itmo.carsharing.fleet.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.created
import ru.itmo.carsharing.fleet.dto.ParkingZoneRequest
import ru.itmo.carsharing.fleet.dto.ParkingZoneResponse
import ru.itmo.carsharing.fleet.dto.VehicleModelRequest
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.service.ParkingZoneService
import ru.itmo.carsharing.fleet.service.VehicleModelService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/vehicle-models")
@Tag(name = "Парк", description = "Машины, модели, зоны, телеметрия бортового блока")
class VehicleModelController(private val models: VehicleModelService) {

    @PostMapping
    @Operation(summary = "Добавить модель в справочник")
    fun create(@Valid @RequestBody request: VehicleModelRequest): ResponseEntity<VehicleModelResponse> {
        val model = models.create(request)
        return created("${ApiPaths.BASE}/vehicle-models/${model.id}", model)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить модель")
    fun get(@PathVariable id: UUID): VehicleModelResponse = models.get(id)

    @GetMapping
    @Operation(summary = "Справочник моделей")
    fun list(
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<VehicleModelResponse> = models.list(page, size)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить модель")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: VehicleModelRequest): VehicleModelResponse =
        models.update(id, request)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить модель, только если на неё нет ссылок")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        models.delete(id)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("${ApiPaths.BASE}/parking-zones")
@Tag(name = "Парк", description = "Машины, модели, зоны, телеметрия бортового блока")
class ParkingZoneController(private val zones: ParkingZoneService) {

    @PostMapping
    @Operation(summary = "Создать зону: центр, радиус, разрешён ли финиш, доплата")
    fun create(@Valid @RequestBody request: ParkingZoneRequest): ResponseEntity<ParkingZoneResponse> {
        val zone = zones.create(request)
        return created("${ApiPaths.BASE}/parking-zones/${zone.id}", zone)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить зону")
    fun get(@PathVariable id: UUID): ParkingZoneResponse = zones.get(id)

    @GetMapping
    @Operation(summary = "Список зон")
    fun list(
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<ParkingZoneResponse> = zones.list(page, size)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить зону")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: ParkingZoneRequest): ParkingZoneResponse =
        zones.update(id, request)

    @DeleteMapping("/{id}")
    @Operation(summary = "Деактивировать зону: на неё ссылаются аренды, физически не удаляется")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        zones.deactivate(id)
        return ResponseEntity.noContent().build()
    }
}
