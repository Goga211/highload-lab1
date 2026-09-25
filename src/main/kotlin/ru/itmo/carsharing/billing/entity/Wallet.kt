package ru.itmo.carsharing.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.PositiveOrZero
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.common.money.Money
import java.math.BigDecimal
import java.util.UUID

/**
 * Счёт клиента. balance уже не включает замороженный депозит: при брони сумма
 * переезжает из balance в heldAmount. balance может уйти в минус, это долг.
 */
@Entity
@Table(name = "wallet")
class Wallet(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
) : BaseEntity() {

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    var balance: BigDecimal = Money.ZERO
        protected set

    @field:PositiveOrZero
    @Column(name = "held_amount", nullable = false, precision = 12, scale = 2)
    var heldAmount: BigDecimal = Money.ZERO
        protected set

    fun credit(amount: BigDecimal) {
        balance = Money.of(balance + amount)
    }

    fun debit(amount: BigDecimal) {
        balance = Money.of(balance - amount)
    }

    fun hold(amount: BigDecimal) {
        require(balance >= amount) { "balance is lower than hold amount" }
        balance = Money.of(balance - amount)
        heldAmount = Money.of(heldAmount + amount)
    }

    fun releaseHold(amount: BigDecimal) {
        require(heldAmount >= amount) { "held amount is lower than release amount" }
        heldAmount = Money.of(heldAmount - amount)
        balance = Money.of(balance + amount)
    }

    /** Снять холд, списать стоимость поездки, остаток депозита вернуть на баланс. */
    fun settle(deposit: BigDecimal, total: BigDecimal) {
        require(heldAmount >= deposit) { "held amount is lower than deposit" }
        heldAmount = Money.of(heldAmount - deposit)
        balance = Money.of(balance + deposit - total)
    }
}
