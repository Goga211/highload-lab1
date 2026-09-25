package ru.itmo.carsharing.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import ru.itmo.carsharing.common.entity.BaseEntity
import java.math.BigDecimal
import java.util.UUID

enum class PaymentType { TOP_UP, DEPOSIT_HOLD, DEPOSIT_RELEASE, RENTAL_CHARGE, FINE_CHARGE, REFUND }

/** PENDING и FAILED понадобятся в ЛР4, когда списания пойдут через Kafka. */
enum class PaymentStatus { PENDING, SUCCEEDED, FAILED }

/** Журнал операций по счёту. Строки не редактируются, повтор операции ловится по idempotencyKey. */
@Entity
@Table(name = "payment")
class Payment(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "rental_id", updatable = false)
    var rentalId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 32, updatable = false)
    var type: PaymentType,

    @field:Positive
    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    var amount: BigDecimal,

    @field:NotBlank
    @Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
    var idempotencyKey: String,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PaymentStatus = PaymentStatus.SUCCEEDED
        protected set
}

/** Ключи идемпотентности: одна бизнес-операция даёт ровно один платёж. */
object IdempotencyKeys {
    fun topUp(userId: UUID, clientKey: String): String = "top-up:$userId:$clientKey"

    fun depositHold(rentalId: UUID): String = "rental:$rentalId:DEPOSIT_HOLD"

    fun depositRelease(rentalId: UUID): String = "rental:$rentalId:DEPOSIT_RELEASE"

    fun rentalCharge(rentalId: UUID): String = "rental:$rentalId:RENTAL_CHARGE"

    fun fineCharge(fineId: UUID): String = "fine:$fineId:FINE_CHARGE"

    fun fineRefund(fineId: UUID): String = "fine:$fineId:REFUND"
}
