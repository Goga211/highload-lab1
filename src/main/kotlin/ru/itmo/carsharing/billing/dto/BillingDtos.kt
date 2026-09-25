package ru.itmo.carsharing.billing.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import ru.itmo.carsharing.billing.entity.PaymentStatus
import ru.itmo.carsharing.billing.entity.PaymentType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TopUpRequest(
    @field:DecimalMin("0.01")
    @field:DecimalMax("1000000.00")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal,
)

data class WalletResponse(
    val userId: UUID,
    val balance: BigDecimal,
    val heldAmount: BigDecimal,
    val updatedAt: Instant,
)

data class PaymentResponse(
    val id: UUID,
    val userId: UUID,
    val rentalId: UUID?,
    val type: PaymentType,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val createdAt: Instant,
)
