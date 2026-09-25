package ru.itmo.carsharing.rentals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import java.time.Instant
import java.util.UUID

class TariffApiIT : IntegrationTest() {

    private fun tariffBody(modelIds: Collection<UUID>, validTo: Instant? = null) = mapOf(
        "name" to "Бизнес",
        "pricePerMinute" to 15,
        "pricePerKm" to 5,
        "waitingPricePerMinute" to 4,
        "freeReservationMinutes" to 15,
        "depositAmount" to 5000,
        "validFrom" to Instant.parse("2026-01-01T00:00:00Z"),
        "validTo" to validTo,
        "modelIds" to modelIds,
    )

    @Test
    fun `tariff CRUD, archive and models`() {
        val bmw = fixtures.model("BUSINESS")
        val octavia = fixtures.model("COMFORT")

        val created = api.post("/api/v1/tariffs", tariffBody(listOf(bmw)))
        val id = created.id()
        val updated = api.put("/api/v1/tariffs/$id", tariffBody(listOf(bmw)) + ("pricePerMinute" to 16))
        val models = api.put("/api/v1/tariffs/$id/models", mapOf("modelIds" to listOf(bmw, octavia)))
        val modelPage = api.get("/api/v1/tariffs/$id/models", "size" to 1)
        val archived = api.post("/api/v1/tariffs/$id/archive")

        assertThat(created.status).isEqualTo(201)
        assertThat(updated.decimal("$.pricePerMinute")).isEqualByComparingTo("16")
        assertThat(models.path<List<String>>("$.modelIds")).hasSize(2)
        assertThat(modelPage.path<Int>("$.totalElements")).isEqualTo(2)
        assertThat(modelPage.path<List<Any>>("$.content")).hasSize(1)
        assertThat(archived.string("$.status")).isEqualTo("ARCHIVED")
        assertThat(api.put("/api/v1/tariffs/$id", tariffBody(listOf(bmw))).status).isEqualTo(409)
        assertThat(api.get("/api/v1/tariffs").path<Int>("$.totalElements")).isEqualTo(1)
    }

    @Test
    fun `tariff validation`() {
        val model = fixtures.model()

        val badRange = api.post("/api/v1/tariffs", tariffBody(listOf(model), validTo = Instant.parse("2025-01-01T00:00:00Z")))
        val unknownModel = api.post("/api/v1/tariffs", tariffBody(listOf(UUID.randomUUID())))
        val negativePrice = api.post("/api/v1/tariffs", tariffBody(listOf(model)) + ("pricePerKm" to 0))

        assertThat(badRange.status).isEqualTo(400)
        assertThat(badRange.path<List<String>>("$.errors[*].field")).contains("validityRangeCorrect")
        assertThat(unknownModel.status).isEqualTo(404)
        assertThat(negativePrice.status).isEqualTo(400)
        assertThat(api.get("/api/v1/tariffs/${UUID.randomUUID()}").status).isEqualTo(404)
    }

    @Test
    fun `rental options CRUD and deactivation`() {
        val created = api.post(
            "/api/v1/rental-options",
            mapOf("code" to "CHILD_SEAT", "name" to "Детское кресло", "price" to 150, "priceUnit" to "PER_RENTAL"),
        )
        val id = created.id()
        val duplicate = api.post(
            "/api/v1/rental-options",
            mapOf("code" to "CHILD_SEAT", "name" to "Кресло", "price" to 100, "priceUnit" to "PER_RENTAL"),
        )
        val badCode = api.post(
            "/api/v1/rental-options",
            mapOf("code" to "child seat", "name" to "Кресло", "price" to 100, "priceUnit" to "PER_RENTAL"),
        )
        val updated = api.put(
            "/api/v1/rental-options/$id",
            mapOf("code" to "CHILD_SEAT", "name" to "Детское кресло", "price" to 200, "priceUnit" to "PER_RENTAL"),
        )
        val codeChange = api.put(
            "/api/v1/rental-options/$id",
            mapOf("code" to "SEAT", "name" to "Кресло", "price" to 200, "priceUnit" to "PER_RENTAL"),
        )

        assertThat(created.status).isEqualTo(201)
        assertThat(duplicate.status).isEqualTo(409)
        assertThat(badCode.status).isEqualTo(400)
        assertThat(updated.decimal("$.price")).isEqualByComparingTo("200")
        assertThat(codeChange.status).isEqualTo(409)
        assertThat(api.delete("/api/v1/rental-options/$id").status).isEqualTo(204)
        assertThat(api.get("/api/v1/rental-options/$id").path<Boolean>("$.active")).isFalse()
        assertThat(api.get("/api/v1/rental-options").path<Int>("$.totalElements")).isEqualTo(1)
    }
}
