package ru.itmo.carsharing.fleet.service

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.CreateMaintenanceTaskRequest
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskDetailsResponse
import ru.itmo.carsharing.fleet.dto.MaintenanceTaskResponse
import ru.itmo.carsharing.fleet.dto.WriteOffPartRequest
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.fleet.entity.MaintenanceTask
import ru.itmo.carsharing.fleet.entity.MaintenanceType
import ru.itmo.carsharing.fleet.entity.Vehicle
import ru.itmo.carsharing.fleet.entity.VehicleStatus
import ru.itmo.carsharing.fleet.mapper.toDetailsResponse
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.MaintenanceTaskRepository
import ru.itmo.carsharing.fleet.repository.SparePartRepository
import ru.itmo.carsharing.fleet.repository.VehicleRepository
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.service.UserDirectory
import java.time.Clock
import java.util.UUID

/**
 * Наряды на обслуживание. Порядок блокировок: машина, потом наряд, потом склад.
 * Тот же порядок в завершении аренды (машина, затем вставка наряда), поэтому взаимных ожиданий нет.
 */
@Service
class MaintenanceService(
    private val tasks: MaintenanceTaskRepository,
    private val vehicles: VehicleRepository,
    private val spareParts: SparePartRepository,
    private val directory: UserDirectory,
    private val clock: Clock,
) {

    /** Ручной наряд: свободная машина уходит в SERVICE тем же условным UPDATE, что и бронь. */
    @Transactional
    fun create(request: CreateMaintenanceTaskRequest): MaintenanceTaskDetailsResponse {
        vehicles.lockById(request.vehicleId) ?: throw NotFoundException("Машина", request.vehicleId)
        val state = vehicles.findState(request.vehicleId) ?: throw NotFoundException("Машина", request.vehicleId)
        if (hasOpenTask(request.vehicleId, request.taskType)) {
            conflict(ErrorCode.DUPLICATE_RESOURCE, "У машины уже есть открытый наряд ${request.taskType}")
        }
        val moved = vehicles.transitionStatus(
            request.vehicleId,
            listOf(VehicleStatus.AVAILABLE.name, VehicleStatus.SERVICE.name),
            VehicleStatus.SERVICE.name,
        )
        if (moved == 0) {
            conflict(
                ErrorCode.VEHICLE_NOT_AVAILABLE,
                "Машина ${state.plateNumber} в статусе ${state.status}, наряд можно открыть на свободную или обслуживаемую",
            )
        }
        val task = openTask(request.vehicleId, request.taskType, state.odometerKm, request.description?.trim())
        return task.toDetailsResponse()
    }

    /** Открыть наряд, если такого же открытого ещё нет. Используется при завершении аренды. */
    @Transactional
    fun openTaskIfAbsent(vehicleId: UUID, type: MaintenanceType, odometerKm: Int, description: String): Boolean {
        if (hasOpenTask(vehicleId, type)) return false
        openTask(vehicleId, type, odometerKm, description)
        return true
    }

    @Transactional(readOnly = true)
    fun list(
        status: MaintenanceStatus?,
        vehicleId: UUID?,
        page: Int,
        size: Int,
    ): PageResponse<MaintenanceTaskResponse> {
        val pageable = Paging.of(page, size, Sort.by("openedAt").descending())
        return PageResponse.from(tasks.findAll(filter(status, vehicleId), pageable)) { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): MaintenanceTaskDetailsResponse =
        (tasks.findWithPartsById(id) ?: throw NotFoundException("Наряд", id)).toDetailsResponse()

    @Transactional
    fun take(id: UUID, mechanicId: UUID): MaintenanceTaskDetailsResponse {
        directory.requireActiveWithRole(mechanicId, UserRole.FLEET_MECHANIC)
        val task = lockTask(id)
        task.take(mechanicId)
        return task.toDetailsResponse()
    }

    /**
     * Списание запчасти: наряд в работе, деталь подходит к модели, остаток уменьшается условным UPDATE.
     * Сущность запчасти читается уже после UPDATE, чтобы в контексте не осталось устаревшего остатка.
     */
    @Transactional
    fun writeOffPart(id: UUID, request: WriteOffPartRequest): MaintenanceTaskDetailsResponse {
        val task = lockTask(id)
        if (task.status != MaintenanceStatus.IN_PROGRESS) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Списывать запчасти можно только в наряд в работе")
        }
        val partId = request.partId
        if (!spareParts.existsById(partId)) throw NotFoundException("Запчасть", partId)
        val modelId = vehicles.findState(task.vehicle.id)?.modelId ?: throw NotFoundException("Машина", task.vehicle.id)
        if (!spareParts.isCompatible(partId, modelId)) {
            unprocessable(ErrorCode.INCOMPATIBLE_PART, "Запчасть $partId не подходит к модели машины")
        }
        if (spareParts.decrementStock(partId, request.quantity) == 0) {
            conflict(ErrorCode.INSUFFICIENT_STOCK, "На складе не хватает запчасти $partId: нужно ${request.quantity}")
        }
        val part = spareParts.findByIdOrNull(partId) ?: throw NotFoundException("Запчасть", partId)
        task.addPart(part, request.quantity, part.price)
        tasks.flush()
        return task.toDetailsResponse()
    }

    /** Закрытие: для ТО фиксируется пробег, для заправки полный бак. Последний закрытый наряд возвращает машину. */
    @Transactional
    fun close(id: UUID): MaintenanceTaskDetailsResponse {
        val vehicleId = lockVehicleOfTask(id)
        val task = lockTask(id)
        task.close(clock.instant())
        when (task.taskType) {
            MaintenanceType.SCHEDULED_SERVICE -> vehicles.markServiced(vehicleId)
            MaintenanceType.REFUELING -> vehicles.markRefueled(vehicleId)
            MaintenanceType.REPAIR, MaintenanceType.WASHING -> Unit
        }
        vehicles.releaseFromServiceIfDone(vehicleId)
        return task.toDetailsResponse()
    }

    /** Отмена: работы не было, поэтому списанные в наряд запчасти возвращаются на склад. */
    @Transactional
    fun cancel(id: UUID): MaintenanceTaskDetailsResponse {
        val vehicleId = lockVehicleOfTask(id)
        val task = lockTask(id)
        task.cancel(clock.instant())
        task.releaseParts().forEach { spareParts.incrementStock(it.part.id, it.quantity) }
        vehicles.releaseFromServiceIfDone(vehicleId)
        return task.toDetailsResponse()
    }

    private fun openTask(
        vehicleId: UUID,
        type: MaintenanceType,
        odometerKm: Int,
        description: String?,
    ): MaintenanceTask {
        val vehicle: Vehicle = vehicles.getReferenceById(vehicleId)
        val task = MaintenanceTask(
            vehicle = vehicle,
            taskType = type,
            description = description,
            openedAt = clock.instant(),
            odometerAtOpenKm = odometerKm,
        )
        return tasks.saveAndFlush(task)
    }

    private fun hasOpenTask(vehicleId: UUID, type: MaintenanceType): Boolean =
        tasks.existsByVehicleIdAndTaskTypeAndStatusIn(vehicleId, type, MaintenanceStatus.OPEN_STATUSES)

    private fun lockVehicleOfTask(taskId: UUID): UUID {
        val vehicleId = tasks.findVehicleIdByTaskId(taskId) ?: throw NotFoundException("Наряд", taskId)
        vehicles.lockById(vehicleId)
        return vehicleId
    }

    private fun lockTask(id: UUID): MaintenanceTask =
        tasks.findByIdForUpdate(id) ?: throw NotFoundException("Наряд", id)

    private fun filter(status: MaintenanceStatus?, vehicleId: UUID?): Specification<MaintenanceTask> =
        Specification { root, _, cb ->
            val predicates = buildList<Predicate> {
                status?.let { add(cb.equal(root.get<MaintenanceStatus>("status"), it)) }
                vehicleId?.let { add(cb.equal(root.get<Vehicle>("vehicle").get<UUID>("id"), it)) }
            }
            cb.and(*predicates.toTypedArray())
        }
}
