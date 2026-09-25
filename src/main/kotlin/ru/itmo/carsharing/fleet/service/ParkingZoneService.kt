package ru.itmo.carsharing.fleet.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.money.Money
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.fleet.dto.ParkingZoneRequest
import ru.itmo.carsharing.fleet.dto.ParkingZoneResponse
import ru.itmo.carsharing.fleet.entity.ParkingZone
import ru.itmo.carsharing.fleet.mapper.toResponse
import ru.itmo.carsharing.fleet.repository.ParkingZoneRepository
import java.util.UUID

@Service
class ParkingZoneService(private val zones: ParkingZoneRepository) {

    @Transactional
    fun create(request: ParkingZoneRequest): ParkingZoneResponse {
        val name = request.name.trim()
        if (zones.existsByName(name)) duplicate(name)
        val zone = ParkingZone(
            name = name,
            zoneType = request.zoneType,
            centerLatitude = request.centerLatitude,
            centerLongitude = request.centerLongitude,
            radiusM = request.radiusM,
            finishAllowed = request.finishAllowed,
            finishSurcharge = Money.of(request.finishSurcharge),
        )
        if (!request.active) zone.deactivate()
        return zones.save(zone).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ParkingZoneResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<ParkingZoneResponse> {
        val pageable = Paging.of(page, size, Sort.by("name"))
        return PageResponse.from(zones.findAll(pageable)) { it.toResponse() }
    }

    @Transactional
    fun update(id: UUID, request: ParkingZoneRequest): ParkingZoneResponse {
        val zone = find(id)
        val name = request.name.trim()
        if (zones.existsByNameAndIdNot(name, id)) duplicate(name)
        zone.name = name
        zone.zoneType = request.zoneType
        zone.centerLatitude = request.centerLatitude
        zone.centerLongitude = request.centerLongitude
        zone.radiusM = request.radiusM
        zone.finishAllowed = request.finishAllowed
        zone.finishSurcharge = Money.of(request.finishSurcharge)
        if (request.active) zone.activate() else zone.deactivate()
        return zone.toResponse()
    }

    /** На зоны ссылаются аренды, поэтому удаление это деактивация. */
    @Transactional
    fun deactivate(id: UUID) {
        find(id).deactivate()
    }

    /** Активная зона с наименьшим радиусом, в которую попадает точка, или null вне всех зон. */
    @Transactional(readOnly = true)
    fun findZoneAt(latitude: Double, longitude: Double): ParkingZone? =
        zones.findZoneIdAt(latitude, longitude)?.let { zones.findByIdOrNull(it) }

    private fun find(id: UUID): ParkingZone = zones.findByIdOrNull(id) ?: throw NotFoundException("Зона", id)

    private fun duplicate(name: String): Nothing =
        conflict(ErrorCode.DUPLICATE_RESOURCE, "Зона с названием \"$name\" уже есть")
}
