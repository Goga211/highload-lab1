package ru.itmo.carsharing.fleet

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest
import ru.itmo.carsharing.support.Places
import java.util.UUID

class FleetApiIT : IntegrationTest() {

    @Test
    fun `vehicle model CRUD with uniqueness`() {
        val body = mapOf(
            "brand" to "Kia",
            "model" to "Rio",
            "vehicleClass" to "ECONOMY",
            "fuelType" to "PETROL",
            "seats" to 5,
            "serviceIntervalKm" to 15000,
        )
        val created = api.post("/api/v1/vehicle-models", body)
        val duplicate = api.post("/api/v1/vehicle-models", body)
        val updated = api.put("/api/v1/vehicle-models/${created.id()}", body + ("seats" to 4))
        val list = api.get("/api/v1/vehicle-models")

        assertThat(created.status).isEqualTo(201)
        assertThat(duplicate.status).isEqualTo(409)
        assertThat(updated.path<Int>("$.seats")).isEqualTo(4)
        assertThat(list.path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(api.delete("/api/v1/vehicle-models/${created.id()}").status).isEqualTo(204)
        assertThat(api.get("/api/v1/vehicle-models/${created.id()}").status).isEqualTo(404)
    }

    @Test
    fun `model with vehicles cannot be deleted`() {
        val modelId = fixtures.model()
        fixtures.vehicle(modelId)

        val response = api.delete("/api/v1/vehicle-models/$modelId")

        assertThat(response.status).isEqualTo(409)
        assertThat(response.errorType()).isEqualTo("resource-in-use")
    }

    @Test
    fun `invalid model fields return 400`() {
        val response = api.post(
            "/api/v1/vehicle-models",
            mapOf(
                "brand" to "",
                "model" to "X",
                "vehicleClass" to "ECONOMY",
                "fuelType" to "PETROL",
                "seats" to 0,
                "serviceIntervalKm" to -1,
            ),
        )

        assertThat(response.status).isEqualTo(400)
        assertThat(response.path<List<String>>("$.errors[*].field")).contains("brand", "seats", "serviceIntervalKm")
    }

    @Test
    fun `zones CRUD and deletion is deactivation`() {
        val zones = fixtures.demoZones()
        val cityId = zones.getValue("city")

        val updated = api.put(
            "/api/v1/parking-zones/$cityId",
            mapOf(
                "name" to "Город",
                "zoneType" to "HOME",
                "centerLatitude" to Places.CITY_LAT,
                "centerLongitude" to Places.CITY_LON,
                "radiusM" to 16000,
                "finishAllowed" to true,
                "finishSurcharge" to 0,
            ),
        )
        val deleted = api.delete("/api/v1/parking-zones/${zones.getValue("airport")}")

        assertThat(updated.path<Int>("$.radiusM")).isEqualTo(16000)
        assertThat(deleted.status).isEqualTo(204)
        assertThat(api.get("/api/v1/parking-zones/${zones.getValue("airport")}").path<Boolean>("$.active")).isFalse()
        assertThat(api.get("/api/v1/parking-zones").path<Int>("$.totalElements")).isEqualTo(3)
        val duplicate = api.post(
            "/api/v1/parking-zones",
            mapOf(
                "name" to "Город",
                "zoneType" to "HOME",
                "centerLatitude" to 1.0,
                "centerLongitude" to 1.0,
                "radiusM" to 10,
                "finishAllowed" to true,
                "finishSurcharge" to 0,
            ),
        )
        assertThat(duplicate.status).isEqualTo(409)
    }

    @Test
    fun `vehicle gets the smallest zone that contains it`() {
        val zones = fixtures.demoZones()
        val modelId = fixtures.model()

        val inCity = api.get("/api/v1/vehicles/${fixtures.vehicle(modelId)}")
        val atAirport = api.get(
            "/api/v1/vehicles/${fixtures.vehicle(modelId, lat = Places.AIRPORT_LAT, lon = Places.AIRPORT_LON)}",
        )
        val outside = api.get(
            "/api/v1/vehicles/${fixtures.vehicle(modelId, lat = Places.OUTSIDE_LAT, lon = Places.OUTSIDE_LON)}",
        )

        assertThat(inCity.uuid("$.currentZoneId")).isEqualTo(zones["city"])
        assertThat(atAirport.uuid("$.currentZoneId")).isEqualTo(zones["airport"])
        assertThat(outside.path<Any?>("$.currentZoneId")).isNull()
        assertThat(inCity.string("$.status")).isEqualTo("AVAILABLE")
    }

    @Test
    fun `catalog returns total count in header and filters by class and status`() {
        val economy = fixtures.model("ECONOMY")
        val business = fixtures.model("BUSINESS")
        repeat(3) { fixtures.vehicle(economy) }
        fixtures.vehicle(business)

        val page = api.get("/api/v1/vehicles", "page" to 0, "size" to 2)
        val businessOnly = api.get("/api/v1/vehicles", "vehicleClass" to "BUSINESS", "status" to "AVAILABLE")

        assertThat(page.status).isEqualTo(200)
        assertThat(page.headers.getFirst("X-Total-Count")).isEqualTo("4")
        assertThat(page.path<List<Any>>("$")).hasSize(2)
        assertThat(businessOnly.headers.getFirst("X-Total-Count")).isEqualTo("1")
    }

    @Test
    fun `page size above 50 is rejected with 400`() {
        val response = api.get("/api/v1/vehicles", "size" to 51)
        val negativePage = api.get("/api/v1/vehicle-models", "page" to -1)

        assertThat(response.status).isEqualTo(400)
        assertThat(response.path<List<String>>("$.errors[*].field")).contains("size")
        assertThat(negativePage.status).isEqualTo(400)
        assertThat(api.get("/api/v1/vehicles", "size" to 50).status).isEqualTo(200)
    }

    @Test
    fun `nearby search returns only available vehicles ordered by distance`() {
        val modelId = fixtures.model()
        val near = fixtures.vehicle(modelId, lat = 59.9390, lon = 30.3150)
        val farther = fixtures.vehicle(modelId, lat = 59.9450, lon = 30.3300)
        fixtures.vehicle(modelId, lat = 59.9800, lon = 30.4500)

        val response = api.get(
            "/api/v1/vehicles/nearby",
            "lat" to Places.CITY_LAT,
            "lon" to Places.CITY_LON,
            "radiusM" to 2000,
            "size" to 1,
        )
        val second = api.get(
            "/api/v1/vehicles/nearby",
            "lat" to Places.CITY_LAT,
            "lon" to Places.CITY_LON,
            "radiusM" to 2000,
            "size" to 1,
            "page" to 1,
        )

        assertThat(response.path<List<String>>("$.content[*].vehicle.id")).containsExactly(near.toString())
        assertThat(response.path<Boolean>("$.hasNext")).isTrue()
        assertThat(second.path<List<String>>("$.content[*].vehicle.id")).containsExactly(farther.toString())
        assertThat(second.path<Boolean>("$.hasNext")).isFalse()
        assertThat(api.get("/api/v1/vehicles/nearby", "lat" to 100, "lon" to 30).status).isEqualTo(400)
    }

    @Test
    fun `telemetry updates position and zone, odometer cannot go back`() {
        val zones = fixtures.demoZones()
        val vehicleId = fixtures.vehicle(fixtures.model(), odometerKm = 1000)

        val moved = fixtures.telemetry(vehicleId, 1030, fuel = 40, lat = Places.AIRPORT_LAT, lon = Places.AIRPORT_LON)
        val back = fixtures.telemetry(vehicleId, 900)

        assertThat(moved.status).isEqualTo(200)
        assertThat(moved.path<Int>("$.odometerKm")).isEqualTo(1030)
        assertThat(moved.uuid("$.currentZoneId")).isEqualTo(zones["airport"])
        assertThat(back.status).isEqualTo(422)
        assertThat(back.errorType()).isEqualTo("telemetry-rejected")
        assertThat(fixtures.telemetry(UUID.randomUUID(), 10).status).isEqualTo(404)
    }

    @Test
    fun `vehicle card update and duplicate identifiers`() {
        val modelId = fixtures.model()
        val otherModel = fixtures.model("COMFORT")
        val request = fixtures.vehicleRequest(modelId)
        val vehicleId = api.post("/api/v1/vehicles", request).id()

        val duplicateVin = api.post("/api/v1/vehicles", fixtures.vehicleRequest(modelId) + ("vin" to request["vin"]!!))
        val badVin = api.post("/api/v1/vehicles", fixtures.vehicleRequest(modelId) + ("vin" to "IOQ12345678901234"))
        val updated = api.put(
            "/api/v1/vehicles/$vehicleId",
            mapOf("plateNumber" to "Х999ХХ78", "modelId" to otherModel),
        )

        assertThat(duplicateVin.status).isEqualTo(409)
        assertThat(badVin.status).isEqualTo(400)
        assertThat(updated.string("$.plateNumber")).isEqualTo("Х999ХХ78")
        assertThat(updated.string("$.model.vehicleClass")).isEqualTo("COMFORT")
    }

    @Test
    fun `decommission keeps the record and blocks further telemetry`() {
        val vehicleId = fixtures.vehicle(fixtures.model())

        val deleted = api.delete("/api/v1/vehicles/$vehicleId")
        val again = api.delete("/api/v1/vehicles/$vehicleId")

        assertThat(deleted.status).isEqualTo(204)
        assertThat(api.get("/api/v1/vehicles/$vehicleId").string("$.status")).isEqualTo("DECOMMISSIONED")
        assertThat(again.status).isEqualTo(409)
        assertThat(fixtures.telemetry(vehicleId, 2000).status).isEqualTo(409)
        assertThat(
            api.put(
                "/api/v1/vehicles/$vehicleId",
                mapOf(
                    "plateNumber" to "Х998ХХ78",
                    "modelId" to fixtures.model(),
                ),
            ).status,
        )
            .isEqualTo(409)
        assertThat(api.delete("/api/v1/vehicles/${UUID.randomUUID()}").status).isEqualTo(404)
    }
}
