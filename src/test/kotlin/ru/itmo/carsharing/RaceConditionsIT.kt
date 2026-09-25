package ru.itmo.carsharing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.carsharing.fleet.repository.VehicleRepository
import ru.itmo.carsharing.fleet.service.FleetOperations
import ru.itmo.carsharing.rentals.repository.RentalRepository
import ru.itmo.carsharing.rentals.service.ReservationExpiryService
import ru.itmo.carsharing.support.IntegrationTest
import ru.itmo.carsharing.support.Places
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Гонки из раздела "Конкурентный доступ" отчёта, воспроизведённые настоящими параллельными транзакциями:
 * старая сущность машины не затирает бронь и телеметрию, планировщик пропускает бронь, которую держит старт.
 */
class RaceConditionsIT : IntegrationTest() {

    @Autowired
    private lateinit var vehicles: VehicleRepository

    @Autowired
    private lateinit var fleet: FleetOperations

    @Autowired
    private lateinit var rentals: RentalRepository

    @Autowired
    private lateinit var expiry: ReservationExpiryService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val outer by lazy { TransactionTemplate(transactionManager) }

    private val concurrent by lazy {
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    }

    @Test
    fun `stale vehicle card cannot overwrite a reservation made meanwhile`() {
        val vehicleId = fixtures.vehicle(fixtures.model())

        assertThatThrownBy {
            outer.executeWithoutResult {
                val stale = vehicles.findWithModelById(vehicleId)!!
                concurrent.executeWithoutResult { fleet.reserve(fleet.getVehicle(vehicleId)) }
                stale.updateCard("Х555ХХ78", stale.model)
                vehicles.saveAndFlush(stale)
            }
        }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)

        val vehicle = api.get("/api/v1/vehicles/$vehicleId")
        assertThat(vehicle.string("$.status")).isEqualTo("RESERVED")
        assertThat(vehicle.string("$.plateNumber")).isNotEqualTo("Х555ХХ78")
    }

    @Test
    fun `stale vehicle card cannot overwrite telemetry received meanwhile`() {
        val vehicleId = fixtures.vehicle(fixtures.model(), odometerKm = 1000)

        assertThatThrownBy {
            outer.executeWithoutResult {
                val stale = vehicles.findWithModelById(vehicleId)!!
                concurrent.executeWithoutResult {
                    vehicles.updateTelemetry(
                        vehicleId,
                        Places.CITY_LAT,
                        Places.CITY_LON,
                        1250,
                        40,
                        null,
                        clock.instant(),
                    )
                }
                stale.updateCard("Х556ХХ78", stale.model)
                vehicles.saveAndFlush(stale)
            }
        }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)

        assertThat(api.get("/api/v1/vehicles/$vehicleId").path<Int>("$.odometerKm")).isEqualTo(1250)
    }

    @Test
    fun `expiry scheduler skips a reservation locked by a concurrent start`() {
        val model = fixtures.model()
        fixtures.basicTariff(listOf(model))
        val vehicleId = fixtures.vehicle(model)
        val rentalId = fixtures.book(fixtures.readyClient(), vehicleId).id()
        clock.advance(Duration.ofMinutes(31))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)

        val holder = thread {
            outer.executeWithoutResult {
                rentals.findByIdForUpdate(rentalId)
                locked.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        locked.await(10, TimeUnit.SECONDS)
        val expiredWhileLocked = expiry.expireOverdueBatch()
        release.countDown()
        holder.join()

        assertThat(expiredWhileLocked).isZero()
        assertThat(expiry.expireOverdueBatch()).isEqualTo(1)
        assertThat(api.get("/api/v1/rentals/$rentalId").string("$.status")).isEqualTo("EXPIRED")
    }
}
