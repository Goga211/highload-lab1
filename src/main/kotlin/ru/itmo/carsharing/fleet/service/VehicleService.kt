package ru.itmo.carsharing.fleet.service

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.geo.Geo
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.SliceResponse
import ru.itmo.carsharing.fleet.dto.CreateVehicleRequest
import ru.itmo.carsharing.fleet.dto.NearbyVehicleResponse
import ru.itmo.carsharing.fleet.dto.TelemetryRequest
import ru.itmo.carsharing.fleet.dto.UpdateVehicleRequest
import ru.itmo.carsharing.fleet.dto.VehicleResponse
import ru.itmo.carsharing.fleet.entity.Vehicle
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.fleet.entity.VehicleModel
import ru.itmo.carsharing.fleet.entity.VehicleStatus
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.VehicleRepository
import java.time.Clock
import java.util.UUID
import kotlin.math.roundToInt

@Service
class VehicleService(
    private val vehicles: VehicleRepository,
    private val models: VehicleModelService,
    private val zones: ParkingZoneService,
    private val clock: Clock,
) {

    @Transactional
    fun create(request: CreateVehicleRequest): VehicleResponse {
        if (vehicles.existsByVin(request.vin)) conflict(ErrorCode.DUPLICATE_RESOURCE, "Машина с VIN ${request.vin} уже есть")
        if (vehicles.existsByPlateNumber(request.plateNumber)) duplicatePlate(request.plateNumber)
        val vehicle = Vehicle(
            vin = request.vin,
            plateNumber = request.plateNumber,
            model = models.find(request.modelId),
            odometerKm = request.odometerKm,
            fuelLevelPercent = request.fuelLevelPercent,
            latitude = request.latitude,
            longitude = request.longitude,
            currentZone = zones.findZoneAt(request.latitude, request.longitude),
            telemetryUpdatedAt = clock.instant(),
        )
        return vehicles.save(vehicle).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): VehicleResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(vehicleClass: VehicleClass?, status: VehicleStatus?, page: Int, size: Int): PageResponse<VehicleResponse> {
        val pageable = Paging.of(page, size, Sort.by("plateNumber"))
        return PageResponse.from(vehicles.findAll(filter(vehicleClass, status), pageable)) { it.toResponse() }
    }

    /** Свободные машины в радиусе, ближайшие первыми. Страница запрашивается с запасом в одну запись. */
    @Transactional(readOnly = true)
    fun nearby(latitude: Double, longitude: Double, radiusM: Int, page: Int, size: Int): SliceResponse<NearbyVehicleResponse> {
        val box = Geo.boundingBox(latitude, longitude, radiusM.toDouble())
        val rows = vehicles.findNearbyAvailable(
            lat = latitude,
            lon = longitude,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
            radiusM = radiusM.toDouble(),
            limit = size + 1,
            offset = page.toLong() * size,
        )
        val pageRows = rows.take(size).map { (it[0] as UUID) to (it[1] as Number).toDouble() }
        val byId = vehicles.findAllByIdIn(pageRows.map { it.first }).associateBy { it.id }
        val content = pageRows.mapNotNull { (id, distance) ->
            byId[id]?.let { NearbyVehicleResponse(distance.roundToInt(), it.toResponse()) }
        }
        return SliceResponse(content, page, size, rows.size > size)
    }

    /** Карточные поля пишутся через JPA с проверкой version: гонка с бронью или телеметрией даст 409. */
    @Transactional
    fun update(id: UUID, request: UpdateVehicleRequest): VehicleResponse {
        val vehicle = find(id)
        if (vehicle.status == VehicleStatus.DECOMMISSIONED) conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Машина списана")
        if (vehicles.existsByPlateNumberAndIdNot(request.plateNumber, id)) duplicatePlate(request.plateNumber)
        vehicle.updateCard(request.plateNumber, models.find(request.modelId))
        return vehicles.saveAndFlush(vehicle).toResponse()
    }

    /** Телеметрия бортового блока пишется отдельным UPDATE только своих колонок. */
    @Transactional
    fun recordTelemetry(id: UUID, request: TelemetryRequest): VehicleResponse {
        val zone = zones.findZoneAt(request.latitude, request.longitude)
        val updated = vehicles.updateTelemetry(
            id = id,
            latitude = request.latitude,
            longitude = request.longitude,
            odometerKm = request.odometerKm,
            fuel = request.fuelLevelPercent,
            zoneId = zone?.id,
            at = clock.instant(),
        )
        if (updated == 0) {
            val state = vehicles.findState(id) ?: throw NotFoundException("Машина", id)
            if (state.status == VehicleStatus.DECOMMISSIONED) {
                conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Машина ${state.plateNumber} списана")
            }
            unprocessable(
                ErrorCode.TELEMETRY_REJECTED,
                "Одометр не может уменьшаться: сейчас ${state.odometerKm} км, пришло ${request.odometerKm} км",
            )
        }
        return find(id).toResponse()
    }

    /** Списание вместо удаления: на машину ссылаются аренды, наряды и штрафы. */
    @Transactional
    fun decommission(id: UUID) {
        vehicles.lockById(id) ?: throw NotFoundException("Машина", id)
        if (vehicles.decommission(id) == 0) {
            conflict(
                ErrorCode.INVALID_STATUS_TRANSITION,
                "Списать можно только свободную машину или машину на обслуживании без открытых нарядов",
            )
        }
    }

    private fun find(id: UUID): Vehicle = vehicles.findWithModelById(id) ?: throw NotFoundException("Машина", id)

    private fun duplicatePlate(plate: String): Nothing =
        conflict(ErrorCode.DUPLICATE_RESOURCE, "Машина с госномером $plate уже есть")

    private fun filter(vehicleClass: VehicleClass?, status: VehicleStatus?): Specification<Vehicle> =
        Specification { root, _, cb ->
            val predicates = buildList<Predicate> {
                vehicleClass?.let {
                    add(cb.equal(root.get<VehicleModel>("model").get<VehicleClass>("vehicleClass"), it))
                }
                status?.let { add(cb.equal(root.get<VehicleStatus>("status"), it)) }
            }
            cb.and(*predicates.toTypedArray())
        }
}
