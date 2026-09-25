package ru.itmo.carsharing.rentals.entity

enum class TariffStatus { ACTIVE, ARCHIVED }

enum class OptionPriceUnit { PER_RENTAL, PER_MINUTE }

/** Жизненный цикл аренды. Любой переход вне этой схемы отклоняется с 409. */
enum class RentalStatus {
    RESERVED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    ;

    fun canTransitionTo(target: RentalStatus): Boolean = when (this) {
        RESERVED -> target == ACTIVE || target == CANCELLED || target == EXPIRED
        ACTIVE -> target == COMPLETED
        COMPLETED, CANCELLED, EXPIRED -> false
    }

    companion object {
        /** Незавершённые аренды: их не больше одной на машину и на клиента. */
        val OPEN_STATUSES: Set<RentalStatus> = setOf(RESERVED, ACTIVE)
    }
}

/**
 * Штраф сначала разбирается (REBILLED клиенту или NO_RENTAL на компании), потом его можно оспорить.
 * Удовлетворённое обжалование отменяет штраф, отклонённое возвращает его в прежний статус.
 */
enum class FineStatus {
    RECEIVED,
    REBILLED,
    NO_RENTAL,
    DISPUTED,
    CANCELLED,
    ;

    fun canTransitionTo(target: FineStatus): Boolean = when (this) {
        RECEIVED -> target == REBILLED || target == NO_RENTAL
        REBILLED, NO_RENTAL -> target == DISPUTED
        DISPUTED -> target == CANCELLED || target == REBILLED || target == NO_RENTAL
        CANCELLED -> false
    }
}
