package ru.itmo.carsharing.rentals.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.common.web.ApiErrors
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.SliceResponse
import ru.itmo.carsharing.common.web.created
import ru.itmo.carsharing.rentals.dto.CancelRentalRequest
import ru.itmo.carsharing.rentals.dto.CreateRentalRequest
import ru.itmo.carsharing.rentals.dto.DisputeFineRequest
import ru.itmo.carsharing.rentals.dto.FineRequest
import ru.itmo.carsharing.rentals.dto.FineResponse
import ru.itmo.carsharing.rentals.dto.RentalResponse
import ru.itmo.carsharing.rentals.entity.FineStatus
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.service.FineService
import ru.itmo.carsharing.rentals.service.RentalQueryService
import ru.itmo.carsharing.rentals.service.RentalService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/rentals")
@Tag(name = "Аренды", description = "Бронь, старт, завершение, отмена, история поездок")
class RentalController(private val commands: RentalService, private val queries: RentalQueryService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors(404, 409, 422)
    @Operation(summary = "Забронировать машину. Депозит замораживается на счёте")
    fun book(@Valid @RequestBody request: CreateRentalRequest): ResponseEntity<RentalResponse> {
        val rental = commands.book(request)
        return created("${ApiPaths.BASE}/rentals/${rental.id}", rental)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Аренда с опциями и итогом")
    fun get(@PathVariable id: UUID): RentalResponse = queries.get(id)

    @GetMapping
    @Operation(summary = "Все аренды с фильтрами")
    fun list(
        @RequestParam(required = false) status: RentalStatus?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<RentalResponse> = queries.list(status, userId, page, size)

    @GetMapping("/history")
    @ApiErrors(404)
    @Operation(summary = "Поездки клиента: бесконечная прокрутка без общего количества")
    fun history(
        @RequestParam userId: UUID,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): SliceResponse<RentalResponse> = queries.history(userId, page, size)

    @PostMapping("/{id}/start")
    @ApiErrors(409)
    @Operation(summary = "Открыть машину: аренда становится активной")
    fun start(@PathVariable id: UUID): RentalResponse = commands.start(id)

    @PostMapping("/{id}/finish")
    @ApiErrors(409, 422)
    @Operation(summary = "Завершить аренду. Координаты и одометр берутся из телеметрии машины")
    fun finish(@PathVariable id: UUID): RentalResponse = commands.finish(id)

    @PostMapping("/{id}/cancel")
    @ApiErrors(409)
    @Operation(summary = "Отменить бронь. Активную аренду отменить нельзя, только завершить")
    fun cancel(@PathVariable id: UUID, @Valid @RequestBody request: CancelRentalRequest): RentalResponse =
        commands.cancel(id, request.reason)
}

@RestController
@RequestMapping("${ApiPaths.BASE}/fines")
@Tag(name = "Штрафы", description = "Постановления ГИБДД и перевыставление клиенту")
class FineController(private val fines: FineService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors(404, 409)
    @Operation(summary = "Ввести постановление")
    fun create(@Valid @RequestBody request: FineRequest): ResponseEntity<FineResponse> {
        val fine = fines.create(request)
        return created("${ApiPaths.BASE}/fines/${fine.id}", fine)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить штраф")
    fun get(@PathVariable id: UUID): FineResponse = fines.get(id)

    @GetMapping
    @Operation(summary = "Список штрафов")
    fun list(
        @RequestParam(required = false) status: FineStatus?,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<FineResponse> = fines.list(status, page, size)

    @PostMapping("/{id}/rebill")
    @ApiErrors(409)
    @Operation(summary = "Найти аренду по машине и времени и списать сумму с клиента")
    fun rebill(@PathVariable id: UUID): FineResponse = fines.rebill(id)

    @PostMapping("/{id}/dispute")
    @ApiErrors(409)
    @Operation(summary = "Оспорить штраф после перевыставления или если аренды не было")
    fun dispute(@PathVariable id: UUID, @Valid @RequestBody request: DisputeFineRequest): FineResponse =
        fines.dispute(id, request.reason)

    @PostMapping("/{id}/reject-dispute")
    @ApiErrors(409)
    @Operation(summary = "Обжалование отклонено: штраф возвращается в REBILLED или NO_RENTAL")
    fun rejectDispute(@PathVariable id: UUID): FineResponse = fines.rejectDispute(id)

    @PostMapping("/{id}/cancel")
    @ApiErrors(409)
    @Operation(summary = "Обжалование удовлетворено: штраф отменяется, списанное возвращается")
    fun cancel(@PathVariable id: UUID): FineResponse = fines.cancel(id)
}
