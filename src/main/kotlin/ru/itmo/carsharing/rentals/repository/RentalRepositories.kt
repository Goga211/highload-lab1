package ru.itmo.carsharing.rentals.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.itmo.carsharing.rentals.entity.FineStatus
import ru.itmo.carsharing.rentals.entity.Rental
import ru.itmo.carsharing.rentals.entity.RentalOption
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.entity.Tariff
import ru.itmo.carsharing.rentals.entity.TariffStatus
import ru.itmo.carsharing.rentals.entity.TrafficFine
import java.time.Instant
import java.util.UUID

interface TariffRepository : JpaRepository<Tariff, UUID> {

    @EntityGraph(attributePaths = ["modelIds"])
    fun findWithModelsById(id: UUID): Tariff?

    /** Действующие тарифы для модели, самый свежий первым. */
    @Query(
        """
        select t from Tariff t join t.modelIds m
        where m = :modelId and t.status = :status and t.validFrom <= :at
          and (t.validTo is null or t.validTo > :at)
        order by t.validFrom desc
        """,
    )
    fun findApplicable(modelId: UUID, at: Instant, status: TariffStatus): List<Tariff>
}

interface RentalOptionRepository : JpaRepository<RentalOption, UUID> {
    fun existsByCode(code: String): Boolean
}

interface RentalRepository :
    JpaRepository<Rental, UUID>,
    JpaSpecificationExecutor<Rental> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rental r where r.id = :id")
    fun findByIdForUpdate(id: UUID): Rental?

    @Query(
        """
        select r from Rental r
        left join fetch r.options o
        left join fetch o.option
        where r.id = :id
        """,
    )
    fun findWithOptionsById(id: UUID): Rental?

    override fun findAll(spec: Specification<Rental>, pageable: Pageable): Page<Rental>

    /** История поездок клиента: Slice не делает count-запрос, общее количество не считается. */
    fun findAllByUserId(userId: UUID, pageable: Pageable): Slice<Rental>

    fun existsByUserIdAndStatusIn(userId: UUID, statuses: Collection<RentalStatus>): Boolean

    /** Аренда, в которой была машина в момент нарушения. */
    @Query(
        """
        select r from Rental r
        where r.vehicleId = :vehicleId and r.status in :statuses
          and r.startedAt <= :at and (r.finishedAt is null or r.finishedAt >= :at)
        order by r.startedAt desc
        """,
    )
    fun findCovering(vehicleId: UUID, at: Instant, statuses: Collection<RentalStatus>): List<Rental>

    /**
     * Просроченные брони пачкой. SKIP LOCKED: два экземпляра приложения не возьмут
     * одну бронь дважды, а бронь, которую прямо сейчас стартует клиент, будет пропущена.
     */
    @Query(
        value = """
        SELECT id FROM rental
        WHERE status = 'RESERVED' AND reserved_at < :cutoff
        ORDER BY reserved_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockOverdueReservations(cutoff: Instant, limit: Int): List<UUID>
}

interface TrafficFineRepository : JpaRepository<TrafficFine, UUID> {
    fun existsByResolutionNumber(number: String): Boolean

    fun findAllByStatus(status: FineStatus, pageable: Pageable): Page<TrafficFine>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from TrafficFine f where f.id = :id")
    fun findByIdForUpdate(id: UUID): TrafficFine?
}
