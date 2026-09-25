package ru.itmo.carsharing.billing.service

import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.billing.dto.PaymentResponse
import ru.itmo.carsharing.billing.dto.WalletResponse
import ru.itmo.carsharing.billing.entity.IdempotencyKeys
import ru.itmo.carsharing.billing.entity.Payment
import ru.itmo.carsharing.billing.entity.PaymentType
import ru.itmo.carsharing.billing.entity.Wallet
import ru.itmo.carsharing.billing.mapper.toResponse
import ru.itmo.carsharing.billing.repository.PaymentRepository
import ru.itmo.carsharing.billing.repository.WalletRepository
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.service.UserRegisteredEvent
import java.math.BigDecimal
import java.util.UUID

@Service
class WalletService(
    private val wallets: WalletRepository,
    private val payments: PaymentRepository,
) {

    /** Счёт открывается в транзакции создания клиента: без клиента нет счёта и наоборот. */
    @EventListener
    fun onUserRegistered(event: UserRegisteredEvent) {
        if (event.role == UserRole.CLIENT) wallets.save(Wallet(event.userId))
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): WalletResponse = (wallets.findByUserId(userId) ?: throw walletNotFound(userId)).toResponse()

    /**
     * Пополнение имитирует оплату картой. Ключ из заголовка Idempotency-Key проверяется
     * после блокировки счёта: два одинаковых запроса подряд не зачислят деньги дважды.
     */
    @Transactional
    fun topUp(userId: UUID, amount: BigDecimal, clientKey: String): WalletResponse {
        val wallet = wallets.findByUserIdForUpdate(userId) ?: throw walletNotFound(userId)
        val key = IdempotencyKeys.topUp(userId, clientKey.trim())
        if (!payments.existsByIdempotencyKey(key)) {
            val value = Money.of(amount)
            wallet.credit(value)
            payments.save(Payment(userId, null, PaymentType.TOP_UP, value, key))
        }
        return wallet.toResponse()
    }

    @Transactional(readOnly = true)
    fun payments(userId: UUID, page: Int, size: Int): PageResponse<PaymentResponse> {
        if (!wallets.existsByUserId(userId)) throw walletNotFound(userId)
        val pageable = Paging.of(page, size, Sort.by("createdAt").descending())
        return PageResponse.from(payments.findAllByUserId(userId, pageable)) { it.toResponse() }
    }

    private fun walletNotFound(userId: UUID) = NotFoundException("Счёт пользователя", userId)
}
