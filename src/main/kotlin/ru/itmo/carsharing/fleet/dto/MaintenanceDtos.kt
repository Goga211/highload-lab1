package ru.itmo.carsharing.fleet.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.fleet.entity.MaintenanceType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private const val MAX_STOCK: Long = 100_000
private const val MAX_WRITE_OFF: Long = 100
private const val MAX_COMPATIBLE_MODELS = 500

data class CreateMaintenanceTaskRequest(
    val vehicleId: UUID,
    val taskType: MaintenanceType,

    @field:Size(max = 500)
    val description: String? = null,
)

/** В ЛР1 механик передаёт свой идентификатор в теле, в ЛР3 он возьмётся из токена. */
data class TakeTaskRequest(val mechanicId: UUID)

data class WriteOffPartRequest(
    val partId: UUID,

    @field:Min(1)
    @field:Max(MAX_WRITE_OFF)
    val quantity: Int,
)

data class MaintenancePartResponse(
    val partId: UUID,
    val article: String,
    val name: String,
    val quantity: Int,
    val unitPriceSnapshot: BigDecimal,
    val totalAmount: BigDecimal,
)

data class MaintenanceTaskResponse(
    val id: UUID,
    val vehicleId: UUID,
    val taskType: MaintenanceType,
    val status: MaintenanceStatus,
    val description: String?,
    val assignedTo: UUID?,
    val openedAt: Instant,
    val closedAt: Instant?,
    val odometerAtOpenKm: Int,
)

data class MaintenanceTaskDetailsResponse(
    val task: MaintenanceTaskResponse,
    val parts: List<MaintenancePartResponse>,
    val partsTotal: BigDecimal,
)

data class SparePartRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,63}$", message = "артикул: латинские заглавные буквы, цифры и дефис")
    val article: String,

    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:PositiveOrZero
    @field:Max(MAX_STOCK)
    val stockQuantity: Int,

    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val price: BigDecimal,
)

data class SparePartResponse(
    val id: UUID,
    val article: String,
    val name: String,
    val stockQuantity: Int,
    val price: BigDecimal,
    val version: Long,
)

data class ModelIdsRequest(
    @field:Size(max = MAX_COMPATIBLE_MODELS)
    val modelIds: Set<UUID>,
)
