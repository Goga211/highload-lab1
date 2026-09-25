package ru.itmo.carsharing.fleet.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.carsharing.fleet.entity.Vehicle
import java.time.Instant
import java.util.UUID

/** Снимок машины для расчёта поездки и нарядов, читается проекцией прямо из базы. */
data class VehicleState(
    val id: UUID,
    val status: ru.itmo.carsharing.fleet.entity.VehicleStatus,
    val modelId: UUID,
    val vehicleClass: ru.itmo.carsharing.fleet.entity.VehicleClass,
    val serviceIntervalKm: Int,
    val odometerKm: Int,
    val lastServiceOdometerKm: Int,
    val fuelLevelPercent: Int,
    val latitude: Double,
    val longitude: Double,
    val currentZoneId: UUID?,
    val plateNumber: String,
)

interface VehicleRepository : JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    @EntityGraph(attributePaths = ["model", "currentZone"])
    fun findWithModelById(id: UUID): Vehicle?

    @EntityGraph(attributePaths = ["model", "currentZone"])
    override fun findAll(spec: Specification<Vehicle>, pageable: Pageable): Page<Vehicle>

    @EntityGraph(attributePaths = ["model", "currentZone"])
    fun findAllByIdIn(ids: Collection<UUID>): List<Vehicle>

    fun existsByModelId(modelId: UUID): Boolean

    fun existsByVin(vin: String): Boolean

    fun existsByPlateNumber(plateNumber: String): Boolean

    fun existsByPlateNumberAndIdNot(plateNumber: String, id: UUID): Boolean

    @Query(
        """
        select new ru.itmo.carsharing.fleet.repository.VehicleState(
            v.id, v.status, m.id, m.vehicleClass, m.serviceIntervalKm, v.odometerKm, v.lastServiceOdometerKm,
            v.fuelLevelPercent, v.latitude, v.longitude, z.id, v.plateNumber)
        from Vehicle v join v.model m left join v.currentZone z
        where v.id = :id
        """,
    )
    fun findState(id: UUID): VehicleState?

    /** Блокировка строки машины: наряды и списание сериализуются по машине. */
    @Query(value = "SELECT id FROM vehicle WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lockById(id: UUID): UUID?

    /**
     * Условный переход статуса. Ноль строк значит, что машину уже заняли или она не в том статусе.
     * version увеличивается, чтобы параллельное сохранение сущности через JPA упало на проверке версии.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET status = :to, version = version + 1, updated_at = now()
        WHERE id = :id AND status IN (:from)
        """,
        nativeQuery = true,
    )
    fun transitionStatus(id: UUID, from: Collection<String>, to: String): Int

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET status = :to, current_zone_id = CAST(:zoneId AS uuid), version = version + 1,
               updated_at = now()
        WHERE id = :id AND status = 'IN_RENTAL'
        """,
        nativeQuery = true,
    )
    fun finishRental(id: UUID, to: String, zoneId: UUID?): Int

    /** Телеметрия пишет только свои колонки. Одометр не может уменьшаться. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET latitude = :latitude, longitude = :longitude, odometer_km = :odometerKm,
               fuel_level_percent = :fuel, current_zone_id = CAST(:zoneId AS uuid), telemetry_updated_at = :at,
               version = version + 1, updated_at = now()
        WHERE id = :id AND status <> 'DECOMMISSIONED' AND odometer_km <= :odometerKm
        """,
        nativeQuery = true,
    )
    fun updateTelemetry(
        id: UUID,
        latitude: Double,
        longitude: Double,
        odometerKm: Int,
        fuel: Int,
        zoneId: UUID?,
        at: Instant,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET last_service_odometer_km = odometer_km, version = version + 1, updated_at = now()
        WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun markServiced(id: UUID): Int

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET fuel_level_percent = 100, version = version + 1, updated_at = now()
        WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun markRefueled(id: UUID): Int

    /** Возврат из обслуживания атомарно проверяет, что открытых нарядов не осталось. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET status = 'AVAILABLE', version = version + 1, updated_at = now()
        WHERE id = :id AND status = 'SERVICE'
          AND NOT EXISTS (SELECT 1 FROM maintenance_task t
                          WHERE t.vehicle_id = :id AND t.status IN ('OPEN', 'IN_PROGRESS'))
        """,
        nativeQuery = true,
    )
    fun releaseFromServiceIfDone(id: UUID): Int

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE vehicle SET status = 'DECOMMISSIONED', version = version + 1, updated_at = now()
        WHERE id = :id AND status IN ('AVAILABLE', 'SERVICE')
          AND NOT EXISTS (SELECT 1 FROM maintenance_task t
                          WHERE t.vehicle_id = :id AND t.status IN ('OPEN', 'IN_PROGRESS'))
        """,
        nativeQuery = true,
    )
    fun decommission(id: UUID): Int

    /**
     * Свободные машины рядом: прямоугольник по частичному индексу, потом гаверсинус.
     * Возвращает пары (id, расстояние в метрах), отсортированные по расстоянию.
     */
    @Query(
        value = """
        SELECT c.id, c.distance_m FROM (
            SELECT v.id,
                   2 * 6371000 * asin(sqrt(
                       power(sin(radians(v.latitude - :lat) / 2), 2) +
                       cos(radians(:lat)) * cos(radians(v.latitude)) *
                       power(sin(radians(v.longitude - :lon) / 2), 2))) AS distance_m
            FROM vehicle v
            WHERE v.status = 'AVAILABLE'
              AND v.latitude BETWEEN :minLat AND :maxLat
              AND v.longitude BETWEEN :minLon AND :maxLon
        ) c
        WHERE c.distance_m <= :radiusM
        ORDER BY c.distance_m, c.id
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findNearbyAvailable(
        lat: Double,
        lon: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        radiusM: Double,
        limit: Int,
        offset: Long,
    ): List<Array<Any>>
}
