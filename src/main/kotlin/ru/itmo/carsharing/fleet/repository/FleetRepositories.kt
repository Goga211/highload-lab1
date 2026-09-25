package ru.itmo.carsharing.fleet.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.carsharing.fleet.entity.MaintenancePart
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.fleet.entity.MaintenanceTask
import ru.itmo.carsharing.fleet.entity.MaintenanceType
import ru.itmo.carsharing.fleet.entity.ParkingZone
import ru.itmo.carsharing.fleet.entity.SparePart
import ru.itmo.carsharing.fleet.entity.VehicleModel
import java.util.UUID

interface VehicleModelRepository : JpaRepository<VehicleModel, UUID> {
    fun existsByBrandAndModel(brand: String, model: String): Boolean

    fun existsByBrandAndModelAndIdNot(brand: String, model: String, id: UUID): Boolean

    fun findAllByIdIn(ids: Collection<UUID>, pageable: Pageable): Page<VehicleModel>

    fun countByIdIn(ids: Collection<UUID>): Long
}

interface ParkingZoneRepository : JpaRepository<ParkingZone, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: UUID): Boolean

    /** Активная зона с наименьшим радиусом, в которую попадает точка. */
    @Query(
        value = """
        SELECT z.id FROM parking_zone z
        WHERE z.is_active
          AND 2 * 6371000 * asin(sqrt(
                power(sin(radians(z.center_latitude - :lat) / 2), 2) +
                cos(radians(:lat)) * cos(radians(z.center_latitude)) *
                power(sin(radians(z.center_longitude - :lon) / 2), 2))) <= z.radius_m
        ORDER BY z.radius_m, z.id
        LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findZoneIdAt(lat: Double, lon: Double): UUID?
}

interface MaintenanceTaskRepository :
    JpaRepository<MaintenanceTask, UUID>,
    JpaSpecificationExecutor<MaintenanceTask> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MaintenanceTask t where t.id = :id")
    fun findByIdForUpdate(id: UUID): MaintenanceTask?

    @Query("select t.vehicle.id from MaintenanceTask t where t.id = :id")
    fun findVehicleIdByTaskId(id: UUID): UUID?

    @EntityGraph(attributePaths = ["vehicle"])
    override fun findAll(spec: Specification<MaintenanceTask>, pageable: Pageable): Page<MaintenanceTask>

    @Query(
        """
        select distinct t from MaintenanceTask t
        join fetch t.vehicle
        left join fetch t.parts p
        left join fetch p.part
        where t.id = :id
        """,
    )
    fun findWithPartsById(id: UUID): MaintenanceTask?

    fun existsByVehicleIdAndTaskTypeAndStatusIn(
        vehicleId: UUID,
        taskType: MaintenanceType,
        statuses: Collection<MaintenanceStatus>,
    ): Boolean

    fun existsByVehicleIdAndStatusIn(vehicleId: UUID, statuses: Collection<MaintenanceStatus>): Boolean
}

interface SparePartRepository : JpaRepository<SparePart, UUID> {
    fun existsByArticle(article: String): Boolean

    fun existsByArticleAndIdNot(article: String, id: UUID): Boolean

    /** Списание со склада: два механика не спишут последнюю деталь дважды. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE spare_part SET stock_quantity = stock_quantity - :quantity, version = version + 1, updated_at = now()
        WHERE id = :id AND stock_quantity >= :quantity
        """,
        nativeQuery = true,
    )
    fun decrementStock(id: UUID, quantity: Int): Int

    /** Возврат на склад при отмене наряда. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        UPDATE spare_part SET stock_quantity = stock_quantity + :quantity, version = version + 1, updated_at = now()
        WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun incrementStock(id: UUID, quantity: Int): Int

    @Query(
        """
        select count(m) > 0 from SparePart p join p.compatibleModels m
        where p.id = :partId and m.id = :modelId
        """,
    )
    fun isCompatible(partId: UUID, modelId: UUID): Boolean

    @Query(
        value = "select m from SparePart p join p.compatibleModels m where p.id = :partId order by m.brand, m.model",
        countQuery = "select count(m) from SparePart p join p.compatibleModels m where p.id = :partId",
    )
    fun findCompatibleModels(partId: UUID, pageable: Pageable): Page<VehicleModel>

    @Query("select count(p) > 0 from SparePart p join p.compatibleModels m where m.id = :modelId")
    fun existsCompatibleWithModel(modelId: UUID): Boolean
}

interface MaintenancePartRepository : JpaRepository<MaintenancePart, UUID> {
    fun existsByPartId(partId: UUID): Boolean
}
