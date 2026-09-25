package ru.itmo.carsharing.users

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import java.time.LocalDate
import java.util.UUID

class UserApiIT : IntegrationTest() {

    private fun userBody(email: String = "ivan@test.local", phone: String = "+79991112233", birthDate: Any = LocalDate.of(1995, 5, 15)) =
        mapOf("email" to email, "phone" to phone, "fullName" to "Иван Клиентов", "birthDate" to birthDate, "role" to "CLIENT")

    @Test
    fun `client is created with 201, location header and an opened wallet`() {
        val response = api.post("/api/v1/users", userBody(email = "Ivan@Test.local"))

        assertThat(response.status).isEqualTo(201)
        assertThat(response.headers.location.toString()).isEqualTo("/api/v1/users/${response.id()}")
        assertThat(response.string("$.email")).isEqualTo("ivan@test.local")
        assertThat(response.string("$.status")).isEqualTo("ACTIVE")
        val wallet = api.get("/api/v1/wallets/${response.id()}")
        assertThat(wallet.status).isEqualTo(200)
        assertThat(wallet.decimal("$.balance")).isEqualByComparingTo("0")
    }

    @Test
    fun `employee gets no wallet`() {
        val supportId = fixtures.createUser("SUPPORT")

        assertThat(api.get("/api/v1/wallets/$supportId").status).isEqualTo(404)
    }

    @Test
    fun `duplicate email and phone are rejected with 409`() {
        api.post("/api/v1/users", userBody())

        val sameEmail = api.post("/api/v1/users", userBody(phone = "+79990000000"))
        val samePhone = api.post("/api/v1/users", userBody(email = "other@test.local"))

        assertThat(sameEmail.status).isEqualTo(409)
        assertThat(sameEmail.errorType()).isEqualTo("duplicate-resource")
        assertThat(samePhone.status).isEqualTo(409)
    }

    @Test
    fun `invalid fields return 400 with the list of violations`() {
        val response = api.post(
            "/api/v1/users",
            userBody(email = "not-an-email", phone = "12", birthDate = LocalDate.now().minusYears(16)),
        )

        assertThat(response.status).isEqualTo(400)
        assertThat(response.headers.contentType.toString()).contains("application/problem+json")
        assertThat(response.errorType()).isEqualTo("validation-failed")
        assertThat(response.path<List<String>>("$.errors[*].field")).contains("email", "phone", "birthDate")
    }

    @Test
    fun `malformed body returns human readable 400`() {
        val response = api.postRaw("/api/v1/users", """{"email": "x@test.local", "role": "PILOT"}""")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.errorType()).isEqualTo("malformed-request")
        assertThat(response.string("$.detail")).isNotBlank()
    }

    @Test
    fun `unknown user returns 404 in problem details format`() {
        val id = UUID.randomUUID()
        val response = api.get("/api/v1/users/$id")

        assertThat(response.status).isEqualTo(404)
        assertThat(response.string("$.title")).isEqualTo("Resource not found")
        assertThat(response.string("$.detail")).contains(id.toString())
        assertThat(response.string("$.instance")).isEqualTo("/api/v1/users/$id")
    }

    @Test
    fun `invalid uuid in path returns 400`() {
        assertThat(api.get("/api/v1/users/not-a-uuid").status).isEqualTo(400)
    }

    @Test
    fun `profile update, block and list by role`() {
        val clientId = fixtures.createUser("CLIENT")
        fixtures.createUser("FLEET_MECHANIC")

        val updated = api.put("/api/v1/users/$clientId", mapOf("fullName" to "Новое Имя", "phone" to "+79995556677"))
        val blocked = api.post("/api/v1/users/$clientId/block")
        val clients = api.get("/api/v1/users", "role" to "CLIENT", "size" to 10)
        val all = api.get("/api/v1/users")

        assertThat(updated.string("$.fullName")).isEqualTo("Новое Имя")
        assertThat(blocked.string("$.status")).isEqualTo("BLOCKED")
        assertThat(clients.path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(all.path<Int>("$.totalElements")).isEqualTo(2)
    }

    @Test
    fun `phone of another user cannot be taken on update`() {
        val first = fixtures.createUser()
        val second = fixtures.createUser()
        val phone = api.get("/api/v1/users/$first").string("$.phone")

        val response = api.put("/api/v1/users/$second", mapOf("fullName" to "Имя", "phone" to phone))

        assertThat(response.status).isEqualTo(409)
    }

    @Test
    fun `enum is stored as string in the database`() {
        val id = fixtures.createUser("FLEET_MECHANIC")

        val role = jdbc.queryForObject("SELECT role FROM app_user WHERE id = ?", String::class.java, id)

        assertThat(role).isEqualTo("FLEET_MECHANIC")
    }
}
