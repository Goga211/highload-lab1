package ru.itmo.carsharing.rentals.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import java.math.BigDecimal

/** Справочник опций: детское кресло, снижение франшизы. Удаление это деактивация. */
@Entity
@Table(name = "rental_option")
class RentalOption(
    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    var code: String,

    @field:NotBlank
    @field:Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @field:Positive
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    var price: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", nullable = false, length = 32)
    var priceUnit: OptionPriceUnit,
) : BaseEntity() {

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true
        protected set

    fun deactivate() {
        active = false
    }

    fun activate() {
        active = true
    }
}

/**
 * Связь многие-ко-многим с полями между арендой и опцией: количество и цена на момент брони.
 * Итог по опции заполняется на финише.
 */
@Entity
@Table(name = "rental_option_item")
class RentalOptionItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rental_id", nullable = false, updatable = false)
    var rental: Rental,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false, updatable = false)
    var option: RentalOption,

    @field:Positive
    @Column(name = "quantity", nullable = false, updatable = false)
    var quantity: Int,

    @field:Positive
    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2, updatable = false)
    var unitPriceSnapshot: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit_snapshot", nullable = false, length = 32, updatable = false)
    var priceUnitSnapshot: OptionPriceUnit,
) : BaseEntity() {

    @field:PositiveOrZero
    @Column(name = "total_amount", precision = 12, scale = 2)
    var totalAmount: BigDecimal? = null
        protected set

    fun charge(amount: BigDecimal) {
        totalAmount = amount
    }
}
