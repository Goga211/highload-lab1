package ru.itmo.carsharing.billing.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.billing.entity.IdempotencyKeys
import ru.itmo.carsharing.billing.entity.Payment
import ru.itmo.carsharing.billing.entity.PaymentType
import ru.itmo.carsharing.billing.entity.Wallet
import ru.itmo.carsharing.billing.repository.PaymentRepository
import ru.itmo.carsharing.billing.repository.WalletRepository
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.money.Money
import java.math.BigDecimal
import java.util.UUID

/** Итог расчёта аренды со счётом: сколько списали и сколько вернули из депозита. */
data class Settlement(val charged: BigDecimal, val released: BigDecimal)

/**
 * Денежные операции для других доменов. Каждая операция идемпотентна по ключу, счёт блокируется
 * PESSIMISTIC_WRITE и всегда последним в транзакции: порядок блокировок аренда, машина, счёт.
 * В ЛР2 это API billing-service, в ЛР4 команды на эти операции пойдут через Kafka.
 */
@Service
class BillingOperations(private val wallets: WalletRepository, private val payments: PaymentRepository) {

    @Transactional
    fun holdDeposit(userId: UUID, rentalId: UUID, amount: BigDecimal) {
        val key = IdempotencyKeys.depositHold(rentalId)
        if (payments.existsByIdempotencyKey(key)) return
        val wallet = lock(userId)
        val deposit = Money.of(amount)
        if (wallet.balance < deposit) {
            unprocessable(
                ErrorCode.INSUFFICIENT_FUNDS,
                "На счёте ${wallet.balance}, для брони нужен депозит $deposit",
            )
        }
        wallet.hold(deposit)
        payments.save(Payment(userId, rentalId, PaymentType.DEPOSIT_HOLD, deposit, key))
    }

    @Transactional
    fun releaseDeposit(userId: UUID, rentalId: UUID, amount: BigDecimal) {
        val key = IdempotencyKeys.depositRelease(rentalId)
        if (payments.existsByIdempotencyKey(key)) return
        val wallet = lock(userId)
        val deposit = Money.of(amount)
        wallet.releaseHold(deposit)
        payments.save(Payment(userId, rentalId, PaymentType.DEPOSIT_RELEASE, deposit, key))
    }

    /**
     * Снять холд, списать стоимость, остаток вернуть. Если поездка дороже депозита, баланс уходит в минус.
     * Стоимость всегда больше нуля (минимум минута по ставке больше нуля), поэтому платёж RENTAL_CHARGE
     * создаётся всегда и его ключ надёжно защищает от повторного расчёта.
     */
    @Transactional
    fun settleRental(userId: UUID, rentalId: UUID, deposit: BigDecimal, total: BigDecimal): Settlement {
        require(total.signum() > 0) { "Rental cost must be positive, got $total" }
        val chargeKey = IdempotencyKeys.rentalCharge(rentalId)
        val released = Money.of(deposit - total).max(Money.ZERO)
        if (payments.existsByIdempotencyKey(chargeKey)) return Settlement(Money.of(total), released)
        val wallet = lock(userId)
        wallet.settle(Money.of(deposit), Money.of(total))
        payments.save(Payment(userId, rentalId, PaymentType.RENTAL_CHARGE, Money.of(total), chargeKey))
        if (released.signum() > 0) {
            payments.save(
                Payment(
                    userId,
                    rentalId,
                    PaymentType.DEPOSIT_RELEASE,
                    released,
                    IdempotencyKeys.depositRelease(rentalId),
                ),
            )
        }
        return Settlement(Money.of(total), released)
    }

    /** Штраф списывается даже в минус: долг блокирует следующие брони. Возвращает id платежа. */
    @Transactional
    fun chargeFine(userId: UUID, rentalId: UUID, fineId: UUID, amount: BigDecimal): UUID {
        val key = IdempotencyKeys.fineCharge(fineId)
        payments.findByIdempotencyKey(key)?.let { return it.id }
        val wallet = lock(userId)
        val value = Money.of(amount)
        wallet.debit(value)
        return payments.save(Payment(userId, rentalId, PaymentType.FINE_CHARGE, value, key)).id
    }

    @Transactional
    fun refundFine(userId: UUID, rentalId: UUID, fineId: UUID, amount: BigDecimal) {
        val key = IdempotencyKeys.fineRefund(fineId)
        if (payments.existsByIdempotencyKey(key)) return
        val wallet = lock(userId)
        val value = Money.of(amount)
        wallet.credit(value)
        payments.save(Payment(userId, rentalId, PaymentType.REFUND, value, key))
    }

    private fun lock(userId: UUID): Wallet = wallets.findByUserIdForUpdate(userId)
        ?: unprocessable(ErrorCode.INSUFFICIENT_FUNDS, "У пользователя $userId нет счёта")
}
