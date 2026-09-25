package ru.itmo.carsharing.fleet.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.common.web.ApiErrors
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.SliceResponse
import ru.itmo.carsharing.common.web.created
import ru.itmo.carsharing.common.web.withTotalCount
import ru.itmo.carsharing.fleet.dto.CreateVehicleRequest
import ru.itmo.carsharing.fleet.dto.NearbyVehicleResponse
import ru.itmo.carsharing.fleet.dto.TelemetryRequest
import ru.itmo.carsharing.fleet.dto.UpdateVehicleRequest
import ru.itmo.carsharing.fleet.dto.VehicleResponse
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.fleet.entity.VehicleStatus
import ru.itmo.carsharing.fleet.service.VehicleRules
import ru.itmo.carsharing.fleet.service.VehicleService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/vehicles")
@Tag(name = "Парк", description = "Машины, модели, зоны, телеметрия бортового блока")
class VehicleController(private val vehicles: VehicleService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors(404, 409)
    @Operation(summary = "Добавить машину")
    fun create(@Valid @RequestBody request: CreateVehicleRequest): ResponseEntity<VehicleResponse> {
        val vehicle = vehicles.create(request)
        return created("${ApiPaths.BASE}/vehicles/${vehicle.id}", vehicle)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Карточка машины с последней телеметрией")
    fun get(@PathVariable id: UUID): VehicleResponse = vehicles.get(id)

    @GetMapping
    @Operation(summary = "Каталог машин. Общее количество в заголовке X-Total-Count")
    fun list(
        @RequestParam(required = false) vehicleClass: VehicleClass?,
        @RequestParam(required = false) status: VehicleStatus?,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): ResponseEntity<List<VehicleResponse>> = withTotalCount(vehicles.list(vehicleClass, status, page, size))

    @GetMapping("/nearby")
    @Operation(summary = "Свободные машины рядом, ближайшие первыми")
    fun nearby(
        @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") lat: Double,
        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") lon: Double,
        @RequestParam(defaultValue = "1000") @Min(1) @Max(VehicleRules.MAX_SEARCH_RADIUS_M) radiusM: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): SliceResponse<NearbyVehicleResponse> = vehicles.nearby(lat, lon, radiusM, page, size)

    @PutMapping("/{id}")
    @ApiErrors(409)
    @Operation(summary = "Изменить карточку: госномер и модель. Модель меняется только у свободной машины")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateVehicleRequest): VehicleResponse =
        vehicles.update(id, request)

    @PostMapping("/{id}/telemetry")
    @ApiErrors(409, 422)
    @Operation(summary = "Бортовой блок присылает координаты, одометр и топливо")
    fun telemetry(@PathVariable id: UUID, @Valid @RequestBody request: TelemetryRequest): VehicleResponse =
        vehicles.recordTelemetry(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiErrors(409)
    @Operation(summary = "Списать машину: статус DECOMMISSIONED, физически запись остаётся")
    fun decommission(@PathVariable id: UUID) {
        vehicles.decommission(id)
    }
}
