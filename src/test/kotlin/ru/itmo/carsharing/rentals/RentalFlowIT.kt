package ru.itmo.carsharing.rentals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.carsharing.rentals.service.ReservationExpiryService
import ru.itmo.carsharing.support.ApiResponse
import ru.itmo.carsharing.support.IntegrationTest
import ru.itmo.carsharing.support.Places
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Обязательные интеграционные тесты ЛР1 из раздела "Тестирование" отчёта. */
class RentalFlowIT : IntegrationTest() {

    @Autowired
    private lateinit var expiry: ReservationExpiryService

    private lateinit var zones: Map<String, UUID>
    private lateinit var support: UUID
    private lateinit var economyModel: UUID
    private lateinit var businessModel: UUID
    private lateinit var basicTariff: UUID
    private lateinit var businessTariff: UUID
    private lateinit var childSeat: UUID
    private lateinit var franchise: UUID
    private lateinit var vehicle: UUID

    @BeforeEach
    fun demoWorld() {
        zones = fixtures.demoZones()
        support = fixtures.createUser("SUPPORT", LocalDate.of(1990, 1, 1))
        economyModel = fixtures.model("ECONOMY", serviceIntervalKm = 15000)
        businessModel = fixtures.model("BUSINESS", brand = "BMW")
        basicTariff = fixtures.basicTariff(listOf(economyModel))
        businessTariff = fixtures.basicTariff(listOf(businessModel), name = "Бизнес", pricePerMinute = 15, deposit = 5000, freeMinutes = 15)
        childSeat = fixtures.option("CHILD_SEAT", 150, "PER_RENTAL")
        franchise = fixtures.option("FRANCHISE_REDUCTION", 2, "PER_MINUTE")
        vehicle = fixtures.vehicle(economyModel, odometerKm = 5000, fuel = 80)
    }

    private fun client(balance: Number = 5000, birthDate: LocalDate = LocalDate.of(1995, 5, 15), firstIssuedAt: LocalDate = LocalDate.of(2019, 6, 1)) =
        fixtures.readyClient(balance = balance, birthDate = birthDate, firstIssuedAt = firstIssuedAt, support = support)

    private fun bothOptions() = listOf(mapOf("optionId" to childSeat, "quantity" to 1), mapOf("optionId" to franchise, "quantity" to 1))

    private fun wallet(userId: UUID) = api.get("/api/v1/wallets/$userId")

    private fun vehicleStatus(id: UUID = vehicle) = api.get("/api/v1/vehicles/$id").string("$.status")

    private fun paymentTypes(userId: UUID) = api.get("/api/v1/wallets/$userId/payments", "size" to 50)
        .path<List<String>>("$.content[*].type")

    /** Сценарий из демо-данных: бронь, старт через 10 минут, 45 минут и 12 км, финиш в городе. */
    private fun completeDemoTrip(clientId: UUID, vehicleId: UUID = vehicle, odometerAfter: Int = 5012): ApiResponse {
        val rentalId = fixtures.book(clientId, vehicleId, bothOptions()).id()
        clock.advance(Duration.ofMinutes(10))
        api.post("/api/v1/rentals/$rentalId/start")
        clock.advance(Duration.ofMinutes(45))
        fixtures.telemetry(vehicleId, odometerAfter, fuel = 70)
        return api.post("/api/v1/rentals/$rentalId/finish")
    }

    @Test
    fun `full path from booking to completed trip matches the demo calculation`() {
        val clientId = client()

        val booked = fixtures.book(clientId, vehicle, bothOptions())
        assertThat(booked.status).isEqualTo(201)
        assertThat(booked.string("$.status")).isEqualTo("RESERVED")
        assertThat(vehicleStatus()).isEqualTo("RESERVED")
        assertThat(wallet(clientId).decimal("$.heldAmount")).isEqualByComparingTo("3000")
        assertThat(wallet(clientId).decimal("$.balance")).isEqualByComparingTo("2000")

        val rentalId = booked.id()
        clock.advance(Duration.ofMinutes(10))
        val started = api.post("/api/v1/rentals/$rentalId/start")
        assertThat(started.string("$.status")).isEqualTo("ACTIVE")
        assertThat(started.path<Int>("$.startOdometerKm")).isEqualTo(5000)
        assertThat(vehicleStatus()).isEqualTo("IN_RENTAL")

        clock.advance(Duration.ofMinutes(45))
        fixtures.telemetry(vehicle, 5012, fuel = 70)
        val finished = api.post("/api/v1/rentals/$rentalId/finish")

        assertThat(finished.status).isEqualTo(200)
        assertThat(finished.string("$.status")).isEqualTo("COMPLETED")
        assertThat(finished.path<Int>("$.cost.durationMinutes")).isEqualTo(45)
        assertThat(finished.path<Int>("$.cost.waitingMinutes")).isZero()
        assertThat(finished.path<Int>("$.cost.distanceKm")).isEqualTo(12)
        assertThat(finished.decimal("$.cost.totalAmount")).isEqualByComparingTo("636")
        assertThat(finished.path<List<Double>>("$.options[*].totalAmount")).containsExactlyInAnyOrder(150.0, 90.0)
        assertThat(finished.uuid("$.finishZoneId")).isEqualTo(zones["city"])
        assertThat(wallet(clientId).decimal("$.balance")).isEqualByComparingTo("4364")
        assertThat(wallet(clientId).decimal("$.heldAmount")).isEqualByComparingTo("0")
        assertThat(paymentTypes(clientId)).containsExactlyInAnyOrder("TOP_UP", "DEPOSIT_HOLD", "RENTAL_CHARGE", "DEPOSIT_RELEASE")
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(api.get("/api/v1/rentals/$rentalId").path<List<Any>>("$.options")).hasSize(2)
    }

    @Test
    fun `two parallel bookings of one vehicle give one 201 and one 409`() {
        val first = client()
        val second = client()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        val statuses = try {
            listOf(first, second)
                .map { clientId -> pool.submit<Int> { start.await(); fixtures.book(clientId, vehicle).status } }
                .also { start.countDown() }
                .map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409)
        assertThat(vehicleStatus()).isEqualTo("RESERVED")
        val rentals = jdbc.queryForObject("SELECT count(*) FROM rental WHERE vehicle_id = ?", Int::class.java, vehicle)
        assertThat(rentals).isEqualTo(1)
    }

    @Test
    fun `booking with balance below deposit gives 422 and leaves vehicle available`() {
        val clientId = client(balance = 1000)

        val response = fixtures.book(clientId, vehicle)

        assertThat(response.status).isEqualTo(422)
        assertThat(response.errorType()).isEqualTo("insufficient-funds")
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(paymentTypes(clientId)).containsExactly("TOP_UP")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental", Int::class.java)).isZero()
    }

    @Test
    fun `finish outside allowed zone gives 422 and changes nothing`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()
        api.post("/api/v1/rentals/$rentalId/start")
        fixtures.telemetry(vehicle, 5020, lat = Places.INDUSTRIAL_LAT, lon = Places.INDUSTRIAL_LON)

        val forbidden = api.post("/api/v1/rentals/$rentalId/finish")
        fixtures.telemetry(vehicle, 5040, lat = Places.OUTSIDE_LAT, lon = Places.OUTSIDE_LON)
        val outside = api.post("/api/v1/rentals/$rentalId/finish")

        assertThat(forbidden.status).isEqualTo(422)
        assertThat(forbidden.errorType()).isEqualTo("finish-zone-forbidden")
        assertThat(forbidden.string("$.detail")).contains("Промзона")
        assertThat(outside.status).isEqualTo(422)
        assertThat(api.get("/api/v1/rentals/$rentalId").string("$.status")).isEqualTo("ACTIVE")
        assertThat(wallet(clientId).decimal("$.heldAmount")).isEqualByComparingTo("3000")
        assertThat(paymentTypes(clientId)).doesNotContain("RENTAL_CHARGE")

        fixtures.telemetry(vehicle, 5060)
        assertThat(api.post("/api/v1/rentals/$rentalId/finish").string("$.status")).isEqualTo("COMPLETED")
    }

    @Test
    fun `finish at the airport adds zone surcharge`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()
        api.post("/api/v1/rentals/$rentalId/start")
        clock.advance(Duration.ofMinutes(30))
        fixtures.telemetry(vehicle, 5020, lat = Places.AIRPORT_LAT, lon = Places.AIRPORT_LON)

        val finished = api.post("/api/v1/rentals/$rentalId/finish")

        assertThat(finished.decimal("$.cost.zoneSurcharge")).isEqualByComparingTo("500")
        assertThat(finished.decimal("$.cost.totalAmount")).isEqualByComparingTo("800")
    }

    @Test
    fun `mileage above threshold creates exactly one service task and repeated finish changes nothing`() {
        val nearService = fixtures.vehicle(economyModel, odometerKm = 14990)
        jdbc.update("UPDATE vehicle SET last_service_odometer_km = 0 WHERE id = ?", nearService)
        val clientId = client()

        val finished = completeDemoTrip(clientId, nearService, odometerAfter = 15002)
        val repeated = api.post("/api/v1/rentals/${finished.id()}/finish")

        assertThat(finished.string("$.status")).isEqualTo("COMPLETED")
        assertThat(repeated.status).isEqualTo(200)
        assertThat(repeated.decimal("$.cost.totalAmount")).isEqualByComparingTo(finished.decimal("$.cost.totalAmount"))
        assertThat(vehicleStatus(nearService)).isEqualTo("SERVICE")
        val tasks = api.get("/api/v1/maintenance-tasks", "vehicleId" to nearService)
        assertThat(tasks.path<List<String>>("$.content[*].taskType")).containsExactly("SCHEDULED_SERVICE")
        val charges = jdbc.queryForObject(
            "SELECT count(*) FROM payment WHERE rental_id = ? AND payment_type = 'RENTAL_CHARGE'",
            Int::class.java,
            finished.id(),
        )
        assertThat(charges).isEqualTo(1)
    }

    @Test
    fun `low fuel after trip creates refueling task`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()
        api.post("/api/v1/rentals/$rentalId/start")
        fixtures.telemetry(vehicle, 5010, fuel = 10)

        api.post("/api/v1/rentals/$rentalId/finish")

        assertThat(vehicleStatus()).isEqualTo("SERVICE")
        assertThat(api.get("/api/v1/maintenance-tasks", "vehicleId" to vehicle).path<List<String>>("$.content[*].taskType"))
            .containsExactly("REFUELING")
    }

    @Test
    fun `telemetry during booking does not reset vehicle status`() {
        val clientId = client()
        fixtures.book(clientId, vehicle)

        val telemetry = fixtures.telemetry(vehicle, 5003)
        val card = api.put("/api/v1/vehicles/$vehicle", mapOf("plateNumber" to "Т777ТТ178", "modelId" to economyModel))

        assertThat(telemetry.string("$.status")).isEqualTo("RESERVED")
        assertThat(card.status).isEqualTo(200)
        assertThat(vehicleStatus()).isEqualTo("RESERVED")
        assertThat(api.get("/api/v1/vehicles/$vehicle").path<Int>("$.odometerKm")).isEqualTo(5003)
    }

    @Test
    fun `reservation expiry returns deposit and frees the vehicle`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()
        clock.advance(Duration.ofMinutes(31))

        val expired = expiry.expireOverdueBatch()

        assertThat(expired).isEqualTo(1)
        assertThat(api.get("/api/v1/rentals/$rentalId").string("$.status")).isEqualTo("EXPIRED")
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(wallet(clientId).decimal("$.balance")).isEqualByComparingTo("5000")
        assertThat(wallet(clientId).decimal("$.heldAmount")).isEqualByComparingTo("0")
        assertThat(expiry.expireOverdueBatch()).isZero()
    }

    @Test
    fun `fresh reservation is not expired and overdue one cannot be started`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()
        clock.advance(Duration.ofMinutes(29))
        assertThat(expiry.expireOverdueBatch()).isZero()

        clock.advance(Duration.ofMinutes(2))
        val start = api.post("/api/v1/rentals/$rentalId/start")

        assertThat(start.status).isEqualTo(409)
        assertThat(start.errorType()).isEqualTo("reservation-expired")
    }

    @Test
    fun `catalog has total count header while trip history is a slice without total`() {
        val clientId = client()
        completeDemoTrip(clientId)
        completeDemoTrip(clientId)

        val catalog = api.get("/api/v1/vehicles")
        val history = api.get("/api/v1/rentals/history", "userId" to clientId, "size" to 1)
        val lastPage = api.get("/api/v1/rentals/history", "userId" to clientId, "size" to 1, "page" to 1)

        assertThat(catalog.headers.getFirst("X-Total-Count")).isEqualTo("1")
        assertThat(history.headers.getFirst("X-Total-Count")).isNull()
        assertThat(history.path<Boolean>("$.hasNext")).isTrue()
        assertThat(history.body).doesNotContain("totalElements")
        assertThat(lastPage.path<Boolean>("$.hasNext")).isFalse()
        assertThat(api.get("/api/v1/rentals/history", "userId" to clientId, "size" to 51).status).isEqualTo(400)
    }

    @Test
    fun `rental status is stored as string`() {
        val clientId = client()
        val rentalId = completeDemoTrip(clientId).id()

        val status = jdbc.queryForObject("SELECT status FROM rental WHERE id = ?", String::class.java, rentalId)

        assertThat(status).isEqualTo("COMPLETED")
    }

    @Test
    fun `tariff change does not change the cost of a closed rental`() {
        val clientId = client()
        val rentalId = completeDemoTrip(clientId).id()

        api.put(
            "/api/v1/tariffs/$basicTariff",
            mapOf(
                "name" to "Базовый", "pricePerMinute" to 100, "pricePerKm" to 30, "waitingPricePerMinute" to 25,
                "freeReservationMinutes" to 0, "depositAmount" to 9000, "validFrom" to Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
        val rental = api.get("/api/v1/rentals/$rentalId")

        assertThat(rental.decimal("$.cost.totalAmount")).isEqualByComparingTo("636")
        assertThat(rental.decimal("$.rates.pricePerMinute")).isEqualByComparingTo("8")
        assertThat(api.get("/api/v1/tariffs/$basicTariff").decimal("$.pricePerMinute")).isEqualByComparingTo("100")
    }

    @Test
    fun `business class requires age 21 and two years of experience`() {
        val bmw = fixtures.vehicle(businessModel)
        val young = client(birthDate = LocalDate.now().minusYears(20), firstIssuedAt = LocalDate.now().minusYears(2))
        val novice = client(firstIssuedAt = LocalDate.now().minusYears(1))
        val experienced = client(balance = 6000)

        val youngResponse = fixtures.book(young, bmw)
        val noviceResponse = fixtures.book(novice, bmw)
        val ok = fixtures.book(experienced, bmw)

        assertThat(youngResponse.status).isEqualTo(422)
        assertThat(youngResponse.errorType()).isEqualTo("driver-not-eligible")
        assertThat(noviceResponse.status).isEqualTo(422)
        assertThat(ok.status).isEqualTo(201)
        assertThat(ok.uuid("$.tariffId")).isEqualTo(businessTariff)
        assertThat(ok.decimal("$.rates.deposit")).isEqualByComparingTo("5000")
    }

    @Test
    fun `client cannot have two open rentals and blocked client cannot book`() {
        val clientId = client()
        val second = fixtures.vehicle(economyModel)
        fixtures.book(clientId, vehicle)

        val another = fixtures.book(clientId, second)
        val blocked = client().also { api.post("/api/v1/users/$it/block") }

        assertThat(another.status).isEqualTo(409)
        assertThat(another.errorType()).isEqualTo("active-rental-exists")
        assertThat(fixtures.book(blocked, second).status).isEqualTo(422)
        assertThat(vehicleStatus(second)).isEqualTo("AVAILABLE")
    }

    @Test
    fun `client without approved license cannot book`() {
        val clientId = fixtures.createUser("CLIENT")
        fixtures.topUp(clientId, 5000)

        val response = fixtures.book(clientId, vehicle)

        assertThat(response.status).isEqualTo(422)
        assertThat(response.errorType()).isEqualTo("license-invalid")
    }

    @Test
    fun `reservation can be cancelled, active rental cannot`() {
        val clientId = client()
        val rentalId = fixtures.book(clientId, vehicle).id()

        val noReason = api.post("/api/v1/rentals/$rentalId/cancel", mapOf("reason" to ""))
        val cancelled = api.post("/api/v1/rentals/$rentalId/cancel", mapOf("reason" to "Передумал"))
        val startCancelled = api.post("/api/v1/rentals/$rentalId/start")

        assertThat(noReason.status).isEqualTo(400)
        assertThat(cancelled.string("$.status")).isEqualTo("CANCELLED")
        assertThat(cancelled.string("$.cancelReason")).isEqualTo("Передумал")
        assertThat(startCancelled.status).isEqualTo(409)
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(wallet(clientId).decimal("$.balance")).isEqualByComparingTo("5000")

        val activeId = fixtures.book(clientId, vehicle).id()
        api.post("/api/v1/rentals/$activeId/start")
        val cancelActive = api.post("/api/v1/rentals/$activeId/cancel", mapOf("reason" to "Поздно"))
        assertThat(cancelActive.status).isEqualTo(409)
        assertThat(cancelActive.errorType()).isEqualTo("invalid-status-transition")
        assertThat(api.post("/api/v1/rentals/$activeId/start").status).isEqualTo(409)
    }

    @Test
    fun `reserved rental cannot be finished`() {
        val rentalId = fixtures.book(client(), vehicle).id()

        assertThat(api.post("/api/v1/rentals/$rentalId/finish").status).isEqualTo(409)
    }

    @Test
    fun `tariff and options must be applicable`() {
        val clientId = client()
        val inactive = fixtures.option("GPS", 50, "PER_RENTAL").also { api.delete("/api/v1/rental-options/$it") }
        val orphanModel = fixtures.model("CARGO")
        val cargo = fixtures.vehicle(orphanModel)

        val wrongTariff = fixtures.book(clientId, vehicle, tariffId = businessTariff)
        val inactiveOption = fixtures.book(clientId, vehicle, listOf(mapOf("optionId" to inactive)))
        val noTariff = fixtures.book(clientId, cargo)
        val duplicateOptions = fixtures.book(clientId, vehicle, listOf(mapOf("optionId" to childSeat), mapOf("optionId" to childSeat)))

        assertThat(wrongTariff.status).isEqualTo(422)
        assertThat(wrongTariff.errorType()).isEqualTo("tariff-not-applicable")
        assertThat(inactiveOption.status).isEqualTo(422)
        assertThat(inactiveOption.errorType()).isEqualTo("option-unavailable")
        assertThat(noTariff.status).isEqualTo(422)
        assertThat(duplicateOptions.status).isEqualTo(400)
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(fixtures.book(clientId, vehicle, tariffId = basicTariff).status).isEqualTo(201)
    }

    @Test
    fun `rentals list filters by status and user, unknown ids give 404`() {
        val first = client()
        val second = client()
        completeDemoTrip(first)
        fixtures.book(second, vehicle)

        val completed = api.get("/api/v1/rentals", "status" to "COMPLETED")
        val bySecond = api.get("/api/v1/rentals", "userId" to second)

        assertThat(completed.path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(bySecond.path<List<String>>("$.content[*].status")).containsExactly("RESERVED")
        assertThat(api.get("/api/v1/rentals/${UUID.randomUUID()}").status).isEqualTo(404)
        assertThat(api.get("/api/v1/rentals/history", "userId" to UUID.randomUUID()).status).isEqualTo(404)
        assertThat(fixtures.book(first, UUID.randomUUID()).status).isEqualTo(404)
        assertThat(api.post("/api/v1/rentals/${UUID.randomUUID()}/start").status).isEqualTo(404)
    }
}
