package ru.itmo.carsharing.fleet.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.common.error.invalidTransition
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Наряд на обслуживание машины. Запчасти списываются в него через [MaintenancePart]. */
@Entity
@Table(name = "maintenance_task")
class MaintenanceTask(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    var vehicle: Vehicle,

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32, updatable = false)
    var taskType: MaintenanceType,

    @field:Size(max = 500)
    @Column(name = "description", length = 500)
    var description: String?,

    @Column(name = "opened_at", nullable = false, updatable = false)
    var openedAt: Instant,

    @field:PositiveOrZero
    @Column(name = "odometer_at_open_km", nullable = false, updatable = false)
    var odometerAtOpenKm: Int,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: MaintenanceStatus = MaintenanceStatus.OPEN
        protected set

    /** Механик. Ссылка в домен users хранится как uuid. */
    @Column(name = "assigned_to")
    var assignedTo: UUID? = null
        protected set

    @Column(name = "closed_at")
    var closedAt: Instant? = null
        protected set

    @OneToMany(mappedBy = "task", cascade = [CascadeType.ALL], orphanRemoval = true)
    var parts: MutableList<MaintenancePart> = mutableListOf()
        protected set

    val isOpen: Boolean
        get() = status in MaintenanceStatus.OPEN_STATUSES

    fun take(mechanicId: UUID) {
        moveTo(MaintenanceStatus.IN_PROGRESS)
        assignedTo = mechanicId
    }

    fun close(at: Instant) {
        moveTo(MaintenanceStatus.DONE)
        closedAt = at
    }

    fun cancel(at: Instant) {
        moveTo(MaintenanceStatus.CANCELLED)
        closedAt = at
    }

    /** Повторное списание той же запчасти увеличивает количество, цена остаётся на момент первого списания. */
    fun addPart(part: SparePart, quantity: Int, unitPrice: BigDecimal): MaintenancePart {
        val existing = parts.firstOrNull { it.part.id == part.id }
        if (existing != null) {
            existing.increase(quantity)
            return existing
        }
        return MaintenancePart(this, part, quantity, unitPrice).also { parts.add(it) }
    }

    private fun moveTo(target: MaintenanceStatus) {
        if (!status.canTransitionTo(target)) invalidTransition("Наряд", status, target)
        status = target
    }
}

/** Склад запчастей. Остаток уменьшается условным UPDATE, version защищает правку карточки. */
@Entity
@Table(name = "spare_part")
class SparePart(
    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "article", nullable = false, length = 64)
    var article: String,

    @field:NotBlank
    @field:Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @field:PositiveOrZero
    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int,

    @field:Positive
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    var price: BigDecimal,
) : BaseEntity() {

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    /** Чистая связь многие-ко-многим: какие запчасти подходят к каким моделям. */
    @ManyToMany
    @JoinTable(
        name = "spare_part_compatibility",
        joinColumns = [JoinColumn(name = "part_id")],
        inverseJoinColumns = [JoinColumn(name = "model_id")],
    )
    var compatibleModels: MutableSet<VehicleModel> = mutableSetOf()
        protected set

    fun update(article: String, name: String, stockQuantity: Int, price: BigDecimal) {
        this.article = article
        this.name = name
        this.stockQuantity = stockQuantity
        this.price = price
    }

    fun replaceCompatibleModels(models: Collection<VehicleModel>) {
        compatibleModels.clear()
        compatibleModels.addAll(models)
    }
}

/** Связь многие-ко-многим с полями: количество и цена запчасти на момент списания. */
@Entity
@Table(name = "maintenance_part")
class MaintenancePart(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    var task: MaintenanceTask,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false, updatable = false)
    var part: SparePart,

    @field:Positive
    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @field:PositiveOrZero
    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2, updatable = false)
    var unitPriceSnapshot: BigDecimal,
) : BaseEntity() {

    fun increase(delta: Int) {
        quantity += delta
    }
}
