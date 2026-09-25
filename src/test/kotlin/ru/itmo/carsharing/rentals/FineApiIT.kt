package ru.itmo.carsharing.rentals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FineApiIT : IntegrationTest() {

    private lateinit var vehicle: UUID
    private lateinit var model: UUID

    @BeforeEach
    fun world() {
        fixtures.demoZones()
        model = fixtures.model()
        fixtures.basicTariff(listOf(model))
        vehicle = fixtures.vehicle(model, odometerKm = 1000)
    }

    private fun fine(at: Instant, amount: Number = 1500, number: String = "188101772609%08d".format(fixtures.next())) =
        api.post(
            "/api/v1/fines",
            mapOf("vehicleId" to vehicle, "resolutionNumber" to number, "violatedAt" to at, "amount" to amount),
        )

    /** Поездка клиента: возвращает интервал, в котором машина была у него. */
    private fun trip(clientId: UUID): Pair<Instant, Instant> {
        val rentalId = fixtures.book(clientId, vehicle).id()
        api.post("/api/v1/rentals/$rentalId/start")
        val startedAt = clock.instant()
        clock.advance(Duration.ofMinutes(40))
        fixtures.telemetry(vehicle, 1010 + fixtures.next())
        api.post("/api/v1/rentals/$rentalId/finish")
        return startedAt to clock.instant()
    }

    @Test
    fun `fine during a rental is rebilled to the driver`() {
        val clientId = fixtures.readyClient(balance = 5000)
        val (startedAt, _) = trip(clientId)
        val balanceAfterTrip = api.get("/api/v1/wallets/$clientId").decimal("$.balance")
        val fineId = fine(startedAt.plus(Duration.ofMinutes(15))).id()

        val rebilled = api.post("/api/v1/fines/$fineId/rebill")

        assertThat(rebilled.status).isEqualTo(200)
        assertThat(rebilled.string("$.status")).isEqualTo("REBILLED")
        assertThat(rebilled.path<String>("$.rentalId")).isNotNull()
        assertThat(rebilled.path<String>("$.paymentId")).isNotNull()
        assertThat(api.get("/api/v1/wallets/$clientId").decimal("$.balance"))
            .isEqualByComparingTo(balanceAfterTrip.subtract("1500".toBigDecimal()))
        assertThat(api.post("/api/v1/fines/$fineId/rebill").status).isEqualTo(409)
    }

    @Test
    fun `fine outside of any rental stays on the company`() {
        val fineId = fine(clock.instant().minus(Duration.ofDays(3))).id()

        val response = api.post("/api/v1/fines/$fineId/rebill")

        assertThat(response.string("$.status")).isEqualTo("NO_RENTAL")
        assertThat(response.path<Any?>("$.paymentId")).isNull()
    }

    @Test
    fun `successful appeal cancels the fine and refunds the client`() {
        val clientId = fixtures.readyClient(balance = 5000)
        val (startedAt, _) = trip(clientId)
        val balanceAfterTrip = api.get("/api/v1/wallets/$clientId").decimal("$.balance")
        val fineId = fine(startedAt.plus(Duration.ofMinutes(5))).id()
        api.post("/api/v1/fines/$fineId/rebill")

        val noReason = api.post("/api/v1/fines/$fineId/dispute", mapOf("reason" to ""))
        val disputed = api.post("/api/v1/fines/$fineId/dispute", mapOf("reason" to "За рулём был другой водитель"))
        val cancelled = api.post("/api/v1/fines/$fineId/cancel")

        assertThat(noReason.status).isEqualTo(400)
        assertThat(disputed.string("$.status")).isEqualTo("DISPUTED")
        assertThat(cancelled.string("$.status")).isEqualTo("CANCELLED")
        assertThat(api.get("/api/v1/wallets/$clientId").decimal("$.balance")).isEqualByComparingTo(balanceAfterTrip)
        val types = api.get("/api/v1/wallets/$clientId/payments", "size" to 50).path<List<String>>("$.content[*].type")
        assertThat(types).contains("FINE_CHARGE", "REFUND")
        assertThat(api.post("/api/v1/fines/$fineId/cancel").status).isEqualTo(409)
    }

    @Test
    fun `rejected appeal returns the fine to the client without moving money`() {
        val clientId = fixtures.readyClient(balance = 5000)
        val (startedAt, _) = trip(clientId)
        val fineId = fine(startedAt.plus(Duration.ofMinutes(5))).id()
        val beforeRebill = api.post("/api/v1/fines/$fineId/dispute", mapOf("reason" to "Рано"))
        api.post("/api/v1/fines/$fineId/rebill")
        val balanceAfterFine = api.get("/api/v1/wallets/$clientId").decimal("$.balance")
        api.post("/api/v1/fines/$fineId/dispute", mapOf("reason" to "Меня там не было"))

        val rejected = api.post("/api/v1/fines/$fineId/reject-dispute")
        val againRejected = api.post("/api/v1/fines/$fineId/reject-dispute")

        assertThat(beforeRebill.status).isEqualTo(409)
        assertThat(rejected.status).isEqualTo(200)
        assertThat(rejected.string("$.status")).isEqualTo("REBILLED")
        assertThat(api.get("/api/v1/wallets/$clientId").decimal("$.balance")).isEqualByComparingTo(balanceAfterFine)
        assertThat(againRejected.status).isEqualTo(409)
    }

    @Test
    fun `rejected appeal of a company fine keeps it on the company`() {
        val fineId = fine(clock.instant().minus(Duration.ofDays(2))).id()
        api.post("/api/v1/fines/$fineId/rebill")
        api.post("/api/v1/fines/$fineId/dispute", mapOf("reason" to "Номер распознан неверно"))

        val rejected = api.post("/api/v1/fines/$fineId/reject-dispute")

        assertThat(rejected.string("$.status")).isEqualTo("NO_RENTAL")
    }

    @Test
    fun `fine debt blocks the next booking`() {
        val clientId = fixtures.readyClient(balance = 3000)
        val (startedAt, _) = trip(clientId)
        val fineId = fine(startedAt.plus(Duration.ofMinutes(1)), amount = 5000).id()
        api.post("/api/v1/fines/$fineId/rebill")

        val booking = fixtures.book(clientId, vehicle)

        assertThat(api.get("/api/v1/wallets/$clientId").decimal("$.balance").signum()).isNegative()
        assertThat(booking.status).isEqualTo(422)
        assertThat(booking.errorType()).isEqualTo("insufficient-funds")
    }

    @Test
    fun `fine validation, uniqueness and listing`() {
        val first = fine(clock.instant().minus(Duration.ofHours(1)), number = "18810177260900000001")

        val duplicate = fine(clock.instant().minus(Duration.ofHours(1)), number = "18810177260900000001")
        val badNumber = fine(clock.instant(), number = "123")
        val future = fine(Instant.now().plus(Duration.ofDays(1)))
        val unknownVehicle = api.post(
            "/api/v1/fines",
            mapOf(
                "vehicleId" to UUID.randomUUID(),
                "resolutionNumber" to "18810177260900000009",
                "violatedAt" to clock.instant(),
                "amount" to 500,
            ),
        )

        assertThat(first.status).isEqualTo(201)
        assertThat(duplicate.status).isEqualTo(409)
        assertThat(badNumber.status).isEqualTo(400)
        assertThat(future.status).isEqualTo(400)
        assertThat(unknownVehicle.status).isEqualTo(404)
        assertThat(api.get("/api/v1/fines", "status" to "RECEIVED").path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(api.get("/api/v1/fines").path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(
            api.get("/api/v1/fines/${first.id()}").string("$.resolutionNumber"),
        ).isEqualTo("18810177260900000001")
        assertThat(api.get("/api/v1/fines/${UUID.randomUUID()}").status).isEqualTo(404)
    }
}
