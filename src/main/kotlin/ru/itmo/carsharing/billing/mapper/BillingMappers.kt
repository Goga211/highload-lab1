package ru.itmo.carsharing.billing.mapper

import ru.itmo.carsharing.billing.dto.PaymentResponse
import ru.itmo.carsharing.billing.dto.WalletResponse
import ru.itmo.carsharing.billing.entity.Payment
import ru.itmo.carsharing.billing.entity.Wallet

fun Wallet.toResponse(): WalletResponse = WalletResponse(
    userId = userId,
    balance = balance,
    heldAmount = heldAmount,
    updatedAt = updatedAt,
)

fun Payment.toResponse(): PaymentResponse = PaymentResponse(
    id = id,
    userId = userId,
    rentalId = rentalId,
    type = type,
    amount = amount,
    status = status,
    createdAt = createdAt,
)
