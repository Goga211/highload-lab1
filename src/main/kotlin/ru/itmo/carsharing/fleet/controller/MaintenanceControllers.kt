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
import ru.itmo.carsharing.fleet.dto.CreateMaintenanceTaskRequest
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskDetailsResponse
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskResponse
import ru.itmo.carsharing.fleet.dto.ModelIdsRequest
import ru.itmo.carsharing.fleet.dto.SparePartRequest
import ru.itmo.carsharing.fleet.dto.SparePartResponse
import ru.itmo.carsharing.fleet.dto.TakeTaskRequest
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.dto.WriteOffPartRequest
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.fleet.service.MaintenanceService
import ru.itmo.carsharing.fleet.service.SparePartService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/maintenance-tasks")
@Tag(name = "Обслуживание", description = "Наряды механиков и склад запчастей")
class MaintenanceController(private val maintenance: MaintenanceService) {

    @PostMapping
    @Operation(summary = "Открыть наряд вручную. Машина должна быть свободна или уже на обслуживании")
    fun create(@Valid @RequestBody request: CreateMaintenanceTaskRequest): ResponseEntity<MaintenanceTaskDetailsResponse> {
        val task = maintenance.create(request)
        return created("${ApiPaths.BASE}/maintenance-tasks/${task.task.id}", task)
    }

    @GetMapping
    @Operation(summary = "Список нарядов")
    fun list(
        @RequestParam(required = false) status: MaintenanceStatus?,
        @RequestParam(required = false) vehicleId: UUID?,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<MaintenanceTaskResponse> = maintenance.list(status, vehicleId, page, size)

    @GetMapping("/{id}")
    @Operation(summary = "Наряд со списанными запчастями")
    fun get(@PathVariable id: UUID): MaintenanceTaskDetailsResponse = maintenance.get(id)

    @PostMapping("/{id}/take")
    @Operation(summary = "Механик берёт наряд в работу")
    fun take(@PathVariable id: UUID, @Valid @RequestBody request: TakeTaskRequest): MaintenanceTaskDetailsResponse =
        maintenance.take(id, request.mechanicId)

    @PostMapping("/{id}/parts")
    @Operation(summary = "Списать запчасть со склада по текущей цене")
    fun writeOffPart(
        @PathVariable id: UUID,
        @Valid @RequestBody request: WriteOffPartRequest,
    ): MaintenanceTaskDetailsResponse = maintenance.writeOffPart(id, request)

    @PostMapping("/{id}/close")
    @Operation(summary = "Закрыть наряд. Для ТО обновляет пробег последнего ТО")
    fun close(@PathVariable id: UUID): MaintenanceTaskDetailsResponse = maintenance.close(id)

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Отменить наряд")
    fun cancel(@PathVariable id: UUID): MaintenanceTaskDetailsResponse = maintenance.cancel(id)
}

@RestController
@RequestMapping("${ApiPaths.BASE}/spare-parts")
@Tag(name = "Обслуживание", description = "Наряды механиков и склад запчастей")
class SparePartController(private val spareParts: SparePartService) {

    @PostMapping
    @Operation(summary = "Добавить запчасть на склад")
    fun create(@Valid @RequestBody request: SparePartRequest): ResponseEntity<SparePartResponse> {
        val part = spareParts.create(request)
        return created("${ApiPaths.BASE}/spare-parts/${part.id}", part)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить запчасть")
    fun get(@PathVariable id: UUID): SparePartResponse = spareParts.get(id)

    @GetMapping
    @Operation(summary = "Склад запчастей")
    fun list(
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<SparePartResponse> = spareParts.list(page, size)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить карточку запчасти и остаток")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: SparePartRequest): SparePartResponse =
        spareParts.update(id, request)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить запчасть, только если её не списывали")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        spareParts.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/models")
    @Operation(summary = "Модели, к которым подходит запчасть")
    fun models(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<VehicleModelResponse> = spareParts.compatibleModels(id, page, size)

    @PutMapping("/{id}/models")
    @Operation(summary = "Заменить список совместимых моделей")
    fun replaceModels(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ModelIdsRequest,
    ): PageResponse<VehicleModelResponse> = spareParts.replaceCompatibleModels(id, request.modelIds)
}
