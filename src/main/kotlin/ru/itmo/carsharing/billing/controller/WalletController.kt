package ru.itmo.carsharing.billing.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.billing.dto.PaymentResponse
import ru.itmo.carsharing.billing.dto.TopUpRequest
import ru.itmo.carsharing.billing.dto.WalletResponse
import ru.itmo.carsharing.billing.service.WalletService
import ru.itmo.carsharing.common.web.ApiErrors
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/wallets")
@Tag(name = "Счета", description = "Баланс клиента, пополнение и выписка")
class WalletController(private val wallets: WalletService) {

    @GetMapping("/{userId}")
    @Operation(summary = "Счёт клиента: баланс и замороженный депозит")
    fun get(@PathVariable userId: UUID): WalletResponse = wallets.get(userId)

    @PostMapping("/{userId}/top-up")
    @ApiErrors(422)
    @Operation(summary = "Пополнить счёт. Повтор с тем же Idempotency-Key не зачисляет деньги второй раз")
    fun topUp(
        @PathVariable userId: UUID,
        @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) idempotencyKey: String,
        @Valid @RequestBody request: TopUpRequest,
    ): WalletResponse = wallets.topUp(userId, request.amount, idempotencyKey)

    @GetMapping("/{userId}/payments")
    @Operation(summary = "Выписка по счёту")
    fun payments(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<PaymentResponse> = wallets.payments(userId, page, size)
}
