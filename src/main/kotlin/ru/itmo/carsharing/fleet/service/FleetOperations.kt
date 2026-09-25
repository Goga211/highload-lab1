package ru.itmo.carsharing.fleet.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.config.CarsharingProperties
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.VehicleModelResponse
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.fleet.entity.MaintenanceType
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.fleet.entity.VehicleStatus
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.MaintenanceTaskRepository
import ru.itmo.carsharing.fleet.repository.VehicleModelRepository
import ru.itmo.carsharing.fleet.repository.VehicleRepository
import ru.itmo.carsharing.fleet.repository.VehicleState
import java.math.BigDecimal
import java.util.UUID

data class VehicleSnapshot(
    val id: UUID,
    val status: VehicleStatus,
    val modelId: UUID,
    val vehicleClass: VehicleClass,
    val plateNumber: String,
)

data class TelemetrySnapshot(
    val odometerKm: Int,
    val fuelLevelPercent: Int,
    val latitude: Double,
    val longitude: Double,
    val zoneId: UUID?,
)

data class ZoneSnapshot(
    val id: UUID,
    val name: String,
    val finishAllowed: Boolean,
    val finishSurcharge: BigDecimal,
)

data class RentalCompletion(val vehicleStatus: VehicleStatus, val createdTasks: List<MaintenanceType>)

/**
 * Операции парка для домена аренд. Статус машины меняется только условными UPDATE:
 * такой запрос одинаково работает в JPA и R2DBC, поэтому переживёт распил на сервисы.
 */
@Service
class FleetOperations(
    private val vehicles: VehicleRepository,
    private val models: VehicleModelRepository,
    private val tasks: MaintenanceTaskRepository,
    private val maintenance: MaintenanceService,
    private val zones: ParkingZoneService,
    private val properties: CarsharingProperties,
) {

    @Transactional(readOnly = true)
    fun getVehicle(vehicleId: UUID): VehicleSnapshot {
        val state = state(vehicleId)
        return VehicleSnapshot(state.id, state.status, state.modelId, state.vehicleClass, state.plateNumber)
    }

    @Transactional(readOnly = true)
    fun ensureVehicleExists(vehicleId: UUID) {
        state(vehicleId)
    }

    /** Захват машины под бронь. Ноль затронутых строк: машину уже взял другой клиент. */
    @Transactional
    fun reserve(vehicle: VehicleSnapshot) {
        val moved = vehicles.transitionStatus(vehicle.id, listOf(VehicleStatus.AVAILABLE.name), VehicleStatus.RESERVED.name)
        if (moved == 0) conflict(ErrorCode.VEHICLE_NOT_AVAILABLE, "Автомобиль ${vehicle.plateNumber} уже забронирован или недоступен")
    }

    @Transactional
    fun releaseReservation(vehicleId: UUID) {
        val moved = vehicles.transitionStatus(vehicleId, listOf(VehicleStatus.RESERVED.name), VehicleStatus.AVAILABLE.name)
        check(moved == 1) { "Vehicle $vehicleId is not RESERVED while its rental is being cancelled" }
    }

    @Transactional
    fun startRental(vehicleId: UUID): TelemetrySnapshot {
        val moved = vehicles.transitionStatus(vehicleId, listOf(VehicleStatus.RESERVED.name), VehicleStatus.IN_RENTAL.name)
        check(moved == 1) { "Vehicle $vehicleId is not RESERVED while its rental is being started" }
        return readTelemetry(vehicleId)
    }

    @Transactional(readOnly = true)
    fun readTelemetry(vehicleId: UUID): TelemetrySnapshot {
        val state = state(vehicleId)
        return TelemetrySnapshot(state.odometerKm, state.fuelLevelPercent, state.latitude, state.longitude, state.currentZoneId)
    }

    @Transactional(readOnly = true)
    fun findZoneAt(latitude: Double, longitude: Double): ZoneSnapshot? =
        zones.findZoneAt(latitude, longitude)?.let { ZoneSnapshot(it.id, it.name, it.finishAllowed, it.finishSurcharge) }

    /**
     * Машина после поездки: пробег с последнего ТО не меньше порога модели даёт наряд ТО,
     * мало топлива даёт наряд на заправку. Есть открытый наряд: машина в SERVICE, иначе AVAILABLE.
     */
    @Transactional
    fun completeRental(vehicleId: UUID, finishZoneId: UUID?): RentalCompletion {
        val state = state(vehicleId)
        val created = buildList {
            if (state.odometerKm - state.lastServiceOdometerKm >= state.serviceIntervalKm &&
                maintenance.openTaskIfAbsent(
                    vehicleId,
                    MaintenanceType.SCHEDULED_SERVICE,
                    state.odometerKm,
                    "Плановое ТО: пробег ${state.odometerKm - state.lastServiceOdometerKm} км с прошлого ТО",
                )
            ) {
                add(MaintenanceType.SCHEDULED_SERVICE)
            }
            if (state.fuelLevelPercent < properties.fleet.fuelLowThresholdPercent &&
                maintenance.openTaskIfAbsent(
                    vehicleId,
                    MaintenanceType.REFUELING,
                    state.odometerKm,
                    "Заправка: после поездки осталось ${state.fuelLevelPercent}% топлива",
                )
            ) {
                add(MaintenanceType.REFUELING)
            }
        }
        val needsService = tasks.existsByVehicleIdAndStatusIn(vehicleId, MaintenanceStatus.OPEN_STATUSES)
        val newStatus = if (needsService) VehicleStatus.SERVICE else VehicleStatus.AVAILABLE
        val moved = vehicles.finishRental(vehicleId, newStatus.name, finishZoneId)
        check(moved == 1) { "Vehicle $vehicleId is not IN_RENTAL while its rental is being finished" }
        return RentalCompletion(newStatus, created)
    }

    @Transactional(readOnly = true)
    fun ensureModelsExist(modelIds: Collection<UUID>) {
        val found = models.findAllById(modelIds).map { it.id }.toSet()
        val missing = modelIds.firstOrNull { it !in found }
        if (missing != null) throw NotFoundException("Модель", missing)
    }

    @Transactional(readOnly = true)
    fun findModels(modelIds: Collection<UUID>, page: Int, size: Int): PageResponse<VehicleModelResponse> {
        val pageable = Paging.of(page, size, Sort.by("brand", "model"))
        return PageResponse.from(models.findAllByIdIn(modelIds, pageable)) { it.toResponse() }
    }

    private fun state(vehicleId: UUID): VehicleState =
        vehicles.findState(vehicleId) ?: throw NotFoundException("Машина", vehicleId)
}
