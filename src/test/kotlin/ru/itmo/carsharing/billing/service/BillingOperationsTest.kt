package ru.itmo.carsharing.billing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.billing.entity.IdempotencyKeys
import ru.itmo.carsharing.billing.entity.Payment
import ru.itmo.carsharing.billing.entity.PaymentType
import ru.itmo.carsharing.billing.entity.Wallet
import ru.itmo.carsharing.billing.repository.PaymentRepository
import ru.itmo.carsharing.billing.repository.WalletRepository
import ru.itmo.carsharing.common.error.ApiException
import ru.itmo.carsharing.common.error.ErrorCode
import java.math.BigDecimal
import java.util.UUID

class BillingOperationsTest {

    private val wallets = mockk<WalletRepository>()
    private val payments = mockk<PaymentRepository>()
    private val billing = BillingOperations(wallets, payments)

    private val userId = UUID.randomUUID()
    private val rentalId = UUID.randomUUID()
    private lateinit var wallet: Wallet

    @BeforeEach
    fun setUp() {
        wallet = Wallet(userId).apply { credit(BigDecimal("5000")) }
        every { wallets.findByUserIdForUpdate(userId) } returns wallet
        every { payments.existsByIdempotencyKey(any()) } returns false
        every { payments.findByIdempotencyKey(any()) } returns null
        every { payments.save(any()) } answers { firstArg() }
    }

    @Test
    fun `hold moves deposit from balance to held amount`() {
        billing.holdDeposit(userId, rentalId, BigDecimal("3000"))

        assertThat(wallet.balance).isEqualByComparingTo("2000")
        assertThat(wallet.heldAmount).isEqualByComparingTo("3000")
        verify {
            payments.save(
                match {
                    it.type == PaymentType.DEPOSIT_HOLD &&
                        it.idempotencyKey == IdempotencyKeys.depositHold(rentalId)
                },
            )
        }
    }

    @Test
    fun `hold fails with 422 when balance is lower than deposit`() {
        assertThatThrownBy { billing.holdDeposit(userId, rentalId, BigDecimal("5000.01")) }
            .isInstanceOf(ApiException::class.java)
            .extracting { (it as ApiException).code }
            .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS)
        assertThat(wallet.heldAmount).isEqualByComparingTo("0")
        verify(exactly = 0) { payments.save(any()) }
    }

    @Test
    fun `repeated hold with the same key does nothing`() {
        every { payments.existsByIdempotencyKey(IdempotencyKeys.depositHold(rentalId)) } returns true

        billing.holdDeposit(userId, rentalId, BigDecimal("3000"))

        assertThat(wallet.balance).isEqualByComparingTo("5000")
        verify(exactly = 0) { wallets.findByUserIdForUpdate(any()) }
    }

    @Test
    fun `settlement charges the trip and returns the rest of deposit`() {
        billing.holdDeposit(userId, rentalId, BigDecimal("3000"))
        val saved = mutableListOf<Payment>()
        every { payments.save(capture(saved)) } answers { firstArg() }

        val settlement = billing.settleRental(userId, rentalId, BigDecimal("3000"), BigDecimal("636"))

        assertThat(settlement.charged).isEqualByComparingTo("636")
        assertThat(settlement.released).isEqualByComparingTo("2364")
        assertThat(wallet.balance).isEqualByComparingTo("4364")
        assertThat(wallet.heldAmount).isEqualByComparingTo("0")
        assertThat(saved.map { it.type }).containsExactly(PaymentType.RENTAL_CHARGE, PaymentType.DEPOSIT_RELEASE)
    }

    @Test
    fun `trip more expensive than deposit puts balance into debt`() {
        billing.holdDeposit(userId, rentalId, BigDecimal("3000"))

        val settlement = billing.settleRental(userId, rentalId, BigDecimal("3000"), BigDecimal("5500"))

        assertThat(settlement.released).isEqualByComparingTo("0")
        assertThat(wallet.balance).isEqualByComparingTo("-500")
    }

    @Test
    fun `repeated settlement does not create a second charge`() {
        every { payments.existsByIdempotencyKey(IdempotencyKeys.rentalCharge(rentalId)) } returns true

        billing.settleRental(userId, rentalId, BigDecimal("3000"), BigDecimal("636"))

        verify(exactly = 0) { payments.save(any()) }
    }

    @Test
    fun `fine charge returns existing payment on repeat`() {
        val fineId = UUID.randomUUID()
        val existing =
            Payment(userId, rentalId, PaymentType.FINE_CHARGE, BigDecimal("500"), IdempotencyKeys.fineCharge(fineId))
        every { payments.findByIdempotencyKey(IdempotencyKeys.fineCharge(fineId)) } returns existing

        val paymentId = billing.chargeFine(userId, rentalId, fineId, BigDecimal("500"))

        assertThat(paymentId).isEqualTo(existing.id)
        assertThat(wallet.balance).isEqualByComparingTo("5000")
    }

    @Test
    fun `fine charge and refund move balance both ways`() {
        val fineId = UUID.randomUUID()
        val saved = slot<Payment>()
        every { payments.save(capture(saved)) } answers { firstArg() }

        billing.chargeFine(userId, rentalId, fineId, BigDecimal("1500"))
        assertThat(wallet.balance).isEqualByComparingTo("3500")
        billing.refundFine(userId, rentalId, fineId, BigDecimal("1500"))

        assertThat(wallet.balance).isEqualByComparingTo("5000")
        assertThat(saved.captured.type).isEqualTo(PaymentType.REFUND)
    }

    @Test
    fun `operation for user without wallet fails with 422`() {
        every { wallets.findByUserIdForUpdate(any()) } returns null

        assertThatThrownBy { billing.holdDeposit(UUID.randomUUID(), rentalId, BigDecimal.TEN) }
            .isInstanceOf(ApiException::class.java)
    }
}
