package ru.itmo.carsharing.billing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import java.util.UUID

class WalletApiIT : IntegrationTest() {

    @Test
    fun `top up is idempotent by key`() {
        val clientId = fixtures.createUser()

        val first = fixtures.topUp(clientId, 1500, key = "pay-1")
        val repeat = fixtures.topUp(clientId, 1500, key = "pay-1")
        val other = fixtures.topUp(clientId, 500, key = "pay-2")

        assertThat(first.decimal("$.balance")).isEqualByComparingTo("1500")
        assertThat(repeat.decimal("$.balance")).isEqualByComparingTo("1500")
        assertThat(other.decimal("$.balance")).isEqualByComparingTo("2000")
        val payments = api.get("/api/v1/wallets/$clientId/payments")
        assertThat(payments.path<Int>("$.totalElements")).isEqualTo(2)
        assertThat(payments.path<List<String>>("$.content[*].type")).containsOnly("TOP_UP")
    }

    @Test
    fun `same idempotency key with another amount is rejected with 422`() {
        val clientId = fixtures.createUser()
        fixtures.topUp(clientId, 1500, key = "pay-1")

        val reused = fixtures.topUp(clientId, 900, key = "pay-1")

        assertThat(reused.status).isEqualTo(422)
        assertThat(reused.errorType()).isEqualTo("idempotency-key-reused")
        assertThat(api.get("/api/v1/wallets/$clientId").decimal("$.balance")).isEqualByComparingTo("1500")
    }

    @Test
    fun `top up without idempotency key or with wrong amount is rejected`() {
        val clientId = fixtures.createUser()

        val noKey = api.post("/api/v1/wallets/$clientId/top-up", mapOf("amount" to 100))
        val negative = fixtures.topUp(clientId, -5)
        val tooPrecise = fixtures.topUp(clientId, 10.001)

        assertThat(noKey.status).isEqualTo(400)
        assertThat(negative.status).isEqualTo(400)
        assertThat(tooPrecise.status).isEqualTo(400)
    }

    @Test
    fun `wallet of unknown user returns 404`() {
        val id = UUID.randomUUID()

        assertThat(api.get("/api/v1/wallets/$id").status).isEqualTo(404)
        assertThat(fixtures.topUp(id, 100).status).isEqualTo(404)
        assertThat(api.get("/api/v1/wallets/$id/payments").status).isEqualTo(404)
    }
}
