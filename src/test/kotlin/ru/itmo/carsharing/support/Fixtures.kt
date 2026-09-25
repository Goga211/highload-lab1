package ru.itmo.carsharing.support

import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Координаты демо-города: центр Санкт-Петербурга, аэропорт и промзона с запретом финиша. */
object Places {
    const val CITY_LAT = 59.9386
    const val CITY_LON = 30.3141
    const val AIRPORT_LAT = 59.8003
    const val AIRPORT_LON = 30.2625
    const val INDUSTRIAL_LAT = 59.8700
    const val INDUSTRIAL_LON = 30.3600
    const val OUTSIDE_LAT = 60.5000
    const val OUTSIDE_LON = 31.5000
}

/** Сборка тестовых данных через публичный API, чтобы тесты шли тем же путём, что и клиент. */
class Fixtures(private val api: Api) {

    private val counter = AtomicInteger()
    private val plateLetters = "АВЕКМНОРСТУХ"

    fun next(): Int = counter.incrementAndGet()

    fun createUser(
        role: String = "CLIENT",
        birthDate: LocalDate = LocalDate.of(1995, 5, 15),
        email: String? = null,
    ): UUID {
        val n = next()
        val response = api.post(
            "/api/v1/users",
            mapOf(
                "email" to (email ?: "user$n@test.local"),
                "phone" to "+7999%07d".format(n),
                "fullName" to "Тестовый Пользователь $n",
                "birthDate" to birthDate,
                "role" to role,
            ),
        )
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    fun addLicense(
        userId: UUID,
        issuedAt: LocalDate = LocalDate.of(2019, 6, 1),
        expiresAt: LocalDate = LocalDate.of(2029, 6, 1),
        firstIssuedAt: LocalDate = issuedAt,
        categories: List<String> = listOf("B"),
        number: String? = null,
    ): ApiResponse = api.post(
        "/api/v1/users/$userId/licenses",
        mapOf(
            "number" to (number ?: "77%08d".format(next())),
            "issuedAt" to issuedAt,
            "expiresAt" to expiresAt,
            "firstIssuedAt" to firstIssuedAt,
            "categories" to categories,
        ),
    )

    fun approveLicense(licenseId: UUID, supportId: UUID): ApiResponse =
        api.post("/api/v1/licenses/$licenseId/approve", mapOf("verifierId" to supportId))

    fun topUp(userId: UUID, amount: Number, key: String = "key-${next()}"): ApiResponse =
        api.post("/api/v1/wallets/$userId/top-up", mapOf("amount" to amount), mapOf("Idempotency-Key" to key))

    /** Клиент с одобренным ВУ (категория B с 2019 года) и пополненным счётом. */
    fun readyClient(
        balance: Number = 5000,
        birthDate: LocalDate = LocalDate.of(1995, 5, 15),
        firstIssuedAt: LocalDate = LocalDate.of(2019, 6, 1),
        support: UUID = createUser("SUPPORT", LocalDate.of(1990, 1, 1)),
    ): UUID {
        val clientId = createUser("CLIENT", birthDate)
        val license = addLicense(clientId, issuedAt = maxOf(firstIssuedAt, LocalDate.of(2019, 6, 1)), firstIssuedAt = firstIssuedAt)
        assertThat(license.status).describedAs(license.toString()).isEqualTo(201)
        val approved = approveLicense(license.id(), support)
        assertThat(approved.status).describedAs(approved.toString()).isEqualTo(200)
        if (balance.toDouble() > 0) {
            val topUp = topUp(clientId, balance)
            assertThat(topUp.status).describedAs(topUp.toString()).isEqualTo(200)
        }
        return clientId
    }

    fun model(vehicleClass: String = "ECONOMY", serviceIntervalKm: Int = 15000, brand: String = "Kia"): UUID {
        val response = api.post(
            "/api/v1/vehicle-models",
            mapOf(
                "brand" to brand,
                "model" to "Model-${next()}",
                "vehicleClass" to vehicleClass,
                "fuelType" to "PETROL",
                "seats" to 5,
                "serviceIntervalKm" to serviceIntervalKm,
            ),
        )
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    fun zone(
        name: String,
        type: String,
        lat: Double,
        lon: Double,
        radiusM: Int,
        finishAllowed: Boolean = true,
        surcharge: Number = 0,
    ): UUID {
        val response = api.post(
            "/api/v1/parking-zones",
            mapOf(
                "name" to name,
                "zoneType" to type,
                "centerLatitude" to lat,
                "centerLongitude" to lon,
                "radiusM" to radiusM,
                "finishAllowed" to finishAllowed,
                "finishSurcharge" to surcharge,
            ),
        )
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    /** Три зоны из демо-данных: город, аэропорт с доплатой, промзона с запретом финиша. */
    fun demoZones(): Map<String, UUID> = mapOf(
        "city" to zone("Город", "HOME", Places.CITY_LAT, Places.CITY_LON, 15000),
        "airport" to zone("Аэропорт", "AIRPORT", Places.AIRPORT_LAT, Places.AIRPORT_LON, 2000, surcharge = 500),
        "industrial" to zone(
            "Промзона",
            "RESTRICTED",
            Places.INDUSTRIAL_LAT,
            Places.INDUSTRIAL_LON,
            1500,
            finishAllowed = false,
        ),
    )

    fun vehicle(
        modelId: UUID,
        odometerKm: Int = 1000,
        fuel: Int = 80,
        lat: Double = Places.CITY_LAT,
        lon: Double = Places.CITY_LON,
    ): UUID {
        val response = api.post("/api/v1/vehicles", vehicleRequest(modelId, odometerKm, fuel, lat, lon))
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    fun vehicleRequest(
        modelId: UUID,
        odometerKm: Int = 1000,
        fuel: Int = 80,
        lat: Double = Places.CITY_LAT,
        lon: Double = Places.CITY_LON,
    ): Map<String, Any> {
        val n = next()
        val plate = "${plateLetters[n % plateLetters.length]}%03d${plateLetters[(n / 12) % 12]}${plateLetters[(n / 144) % 12]}178"
            .format(n % 1000)
        return mapOf(
            "vin" to "Z94K241BAMR%06d".format(n),
            "plateNumber" to plate,
            "modelId" to modelId,
            "latitude" to lat,
            "longitude" to lon,
            "odometerKm" to odometerKm,
            "fuelLevelPercent" to fuel,
        )
    }

    fun telemetry(vehicleId: UUID, odometerKm: Int, fuel: Int = 70, lat: Double = Places.CITY_LAT, lon: Double = Places.CITY_LON): ApiResponse =
        api.post(
            "/api/v1/vehicles/$vehicleId/telemetry",
            mapOf("latitude" to lat, "longitude" to lon, "odometerKm" to odometerKm, "fuelLevelPercent" to fuel),
        )

    /** Тариф "Базовый" из демо-данных: 8 за минуту, 3 за км, 2.5 за минуту ожидания, 20 минут, депозит 3000. */
    fun basicTariff(
        modelIds: Collection<UUID>,
        name: String = "Базовый",
        pricePerMinute: Number = 8,
        deposit: Number = 3000,
        freeMinutes: Int = 20,
    ): UUID {
        val response = api.post(
            "/api/v1/tariffs",
            mapOf(
                "name" to name,
                "pricePerMinute" to pricePerMinute,
                "pricePerKm" to 3,
                "waitingPricePerMinute" to 2.5,
                "freeReservationMinutes" to freeMinutes,
                "depositAmount" to deposit,
                "validFrom" to Instant.parse("2026-01-01T00:00:00Z"),
                "modelIds" to modelIds,
            ),
        )
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    fun option(code: String, price: Number, unit: String): UUID {
        val response = api.post(
            "/api/v1/rental-options",
            mapOf("code" to code, "name" to "Опция $code", "price" to price, "priceUnit" to unit),
        )
        assertThat(response.status).describedAs(response.toString()).isEqualTo(201)
        return response.id()
    }

    fun book(userId: UUID, vehicleId: UUID, options: List<Map<String, Any>> = emptyList(), tariffId: UUID? = null): ApiResponse =
        api.post(
            "/api/v1/rentals",
            buildMap {
                put("userId", userId)
                put("vehicleId", vehicleId)
                put("options", options)
                if (tariffId != null) put("tariffId", tariffId)
            },
        )
}
