package ru.itmo.carsharing.rentals.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.created
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.rentals.dto.RentalOptionRequest
import ru.itmo.carsharing.rentals.dto.RentalOptionResponse
import ru.itmo.carsharing.rentals.dto.TariffModelsRequest
import ru.itmo.carsharing.rentals.dto.TariffRequest
import ru.itmo.carsharing.rentals.dto.TariffResponse
import ru.itmo.carsharing.rentals.service.RentalOptionService
import ru.itmo.carsharing.rentals.service.TariffService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/tariffs")
@Tag(name = "Тарифы и опции", description = "Прайс и дополнительные опции аренды")
class TariffController(private val tariffs: TariffService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors(404, 422)
    @Operation(summary = "Создать тариф и привязать к моделям")
    fun create(@Valid @RequestBody request: TariffRequest): ResponseEntity<TariffResponse> {
        val tariff = tariffs.create(request)
        return created("${ApiPaths.BASE}/tariffs/${tariff.id}", tariff)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить тариф")
    fun get(@PathVariable id: UUID): TariffResponse = tariffs.get(id)

    @GetMapping
    @Operation(summary = "Список тарифов")
    fun list(
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<TariffResponse> = tariffs.list(page, size)

    @PutMapping("/{id}")
    @ApiErrors(409, 422)
    @Operation(summary = "Заменить тариф целиком, включая модели. Уже созданные аренды сохраняют свои ставки")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: TariffRequest): TariffResponse =
        tariffs.update(id, request)

    @PostMapping("/{id}/archive")
    @Operation(summary = "Архивировать тариф")
    fun archive(@PathVariable id: UUID): TariffResponse = tariffs.archive(id)

    @GetMapping("/{id}/models")
    @Operation(summary = "Модели, к которым применим тариф")
    fun models(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<VehicleModelResponse> = tariffs.models(id, page, size)

    @PutMapping("/{id}/models")
    @Operation(summary = "Заменить список моделей тарифа")
    fun replaceModels(@PathVariable id: UUID, @Valid @RequestBody request: TariffModelsRequest): TariffResponse =
        tariffs.replaceModels(id, request.modelIds)
}

@RestController
@RequestMapping("${ApiPaths.BASE}/rental-options")
@Tag(name = "Тарифы и опции", description = "Прайс и дополнительные опции аренды")
class RentalOptionController(private val options: RentalOptionService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors(409)
    @Operation(summary = "Создать опцию")
    fun create(@Valid @RequestBody request: RentalOptionRequest): ResponseEntity<RentalOptionResponse> {
        val option = options.create(request)
        return created("${ApiPaths.BASE}/rental-options/${option.id}", option)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить опцию")
    fun get(@PathVariable id: UUID): RentalOptionResponse = options.get(id)

    @GetMapping
    @Operation(summary = "Справочник опций")
    fun list(
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<RentalOptionResponse> = options.list(page, size)

    @PutMapping("/{id}")
    @ApiErrors(422)
    @Operation(summary = "Изменить опцию. Код опции неизменяем")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: RentalOptionRequest): RentalOptionResponse =
        options.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Деактивировать опцию")
    fun delete(@PathVariable id: UUID) {
        options.deactivate(id)
    }
}
