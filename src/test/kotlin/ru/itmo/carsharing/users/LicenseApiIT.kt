package ru.itmo.carsharing.users

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import java.time.LocalDate
import java.util.UUID

class LicenseApiIT : IntegrationTest() {

    private lateinit var clientId: UUID
    private lateinit var supportId: UUID

    @BeforeEach
    fun users() {
        clientId = fixtures.createUser("CLIENT", LocalDate.of(1995, 5, 15))
        supportId = fixtures.createUser("SUPPORT")
    }

    @Test
    fun `license goes through pending to approved`() {
        val added = fixtures.addLicense(clientId, number = "77 ав 123456", categories = listOf("B", "BE"))

        assertThat(added.status).isEqualTo(201)
        assertThat(added.string("$.number")).isEqualTo("77АВ123456")
        assertThat(added.string("$.verificationStatus")).isEqualTo("PENDING")

        val approved = fixtures.approveLicense(added.id(), supportId)
        assertThat(approved.status).isEqualTo(200)
        assertThat(approved.string("$.verificationStatus")).isEqualTo("APPROVED")
        assertThat(approved.uuid("$.verifiedBy")).isEqualTo(supportId)
    }

    @Test
    fun `new approved license replaces the previous one`() {
        val first = fixtures.addLicense(clientId).id()
        fixtures.approveLicense(first, supportId)
        val second = fixtures.addLicense(clientId, issuedAt = LocalDate.of(2024, 2, 1), expiresAt = LocalDate.of(2034, 2, 1), firstIssuedAt = LocalDate.of(2019, 6, 1)).id()

        fixtures.approveLicense(second, supportId)

        assertThat(api.get("/api/v1/licenses/$first").string("$.verificationStatus")).isEqualTo("REPLACED")
        assertThat(api.get("/api/v1/licenses/$second").string("$.verificationStatus")).isEqualTo("APPROVED")
        val history = api.get("/api/v1/users/$clientId/licenses")
        assertThat(history.path<Int>("$.totalElements")).isEqualTo(2)
    }

    @Test
    fun `license approved twice returns 409`() {
        val id = fixtures.addLicense(clientId).id()
        fixtures.approveLicense(id, supportId)

        val again = fixtures.approveLicense(id, supportId)

        assertThat(again.status).isEqualTo(409)
        assertThat(again.errorType()).isEqualTo("invalid-status-transition")
    }

    @Test
    fun `only active support can verify licenses`() {
        val mechanic = fixtures.createUser("FLEET_MECHANIC")
        val id = fixtures.addLicense(clientId).id()

        val response = fixtures.approveLicense(id, mechanic)

        assertThat(response.status).isEqualTo(422)
        assertThat(response.errorType()).isEqualTo("wrong-user-role")
    }

    @Test
    fun `reject requires reason and stores it`() {
        val id = fixtures.addLicense(clientId).id()

        val noReason = api.post("/api/v1/licenses/$id/reject", mapOf("verifierId" to supportId, "reason" to " "))
        val rejected = api.post("/api/v1/licenses/$id/reject", mapOf("verifierId" to supportId, "reason" to "Фото нечитаемо"))
        val approveAfterReject = fixtures.approveLicense(id, supportId)

        assertThat(noReason.status).isEqualTo(400)
        assertThat(rejected.string("$.verificationStatus")).isEqualTo("REJECTED")
        assertThat(rejected.string("$.rejectionReason")).isEqualTo("Фото нечитаемо")
        assertThat(approveAfterReject.status).isEqualTo(409)
    }

    @Test
    fun `inconsistent dates are rejected with field errors`() {
        val response = fixtures.addLicense(
            clientId,
            issuedAt = LocalDate.of(2015, 1, 1),
            expiresAt = LocalDate.of(2030, 1, 2),
            firstIssuedAt = LocalDate.of(2016, 1, 1),
            categories = listOf("A"),
        )

        assertThat(response.status).isEqualTo(400)
        assertThat(response.path<List<String>>("$.errors[*].field")).contains("expiresAt", "firstIssuedAt", "categories")
    }

    @Test
    fun `expired license and bad number format are rejected`() {
        val expired = fixtures.addLicense(clientId, issuedAt = LocalDate.of(2012, 1, 1), expiresAt = LocalDate.of(2022, 1, 1))
        val badNumber = fixtures.addLicense(clientId, number = "12-34")

        assertThat(expired.status).isEqualTo(400)
        assertThat(badNumber.status).isEqualTo(400)
        assertThat(badNumber.path<List<String>>("$.errors[*].field")).contains("number")
    }

    @Test
    fun `category B obtained before 18 is rejected with 422`() {
        val response = fixtures.addLicense(
            clientId,
            issuedAt = LocalDate.of(2020, 1, 1),
            expiresAt = LocalDate.of(2030, 1, 1),
            firstIssuedAt = LocalDate.of(2012, 6, 1),
        )

        assertThat(response.status).isEqualTo(422)
        assertThat(response.errorType()).isEqualTo("license-invalid")
    }

    @Test
    fun `license number is unique and employees cannot add licenses`() {
        fixtures.addLicense(clientId, number = "7812345678")
        val otherClient = fixtures.createUser("CLIENT")

        val duplicate = fixtures.addLicense(otherClient, number = "7812345678")
        val forSupport = fixtures.addLicense(supportId)

        assertThat(duplicate.status).isEqualTo(409)
        assertThat(forSupport.status).isEqualTo(422)
    }

    @Test
    fun `license of unknown user returns 404`() {
        assertThat(fixtures.addLicense(UUID.randomUUID()).status).isEqualTo(404)
        assertThat(api.get("/api/v1/users/${UUID.randomUUID()}/licenses").status).isEqualTo(404)
        assertThat(api.get("/api/v1/licenses/${UUID.randomUUID()}").status).isEqualTo(404)
    }
}
