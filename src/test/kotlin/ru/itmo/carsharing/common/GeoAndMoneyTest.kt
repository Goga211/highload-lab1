package ru.itmo.carsharing.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.common.geo.Geo
import ru.itmo.carsharing.common.money.Money
import java.math.BigDecimal
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class GeoAndMoneyTest {

    /** Та же формула гаверсинуса, что в SQL поиска машин рядом. */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = sin(Math.toRadians(lat2 - lat1) / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(a))
    }

    @Test
    fun `bounding box edges lie at the search radius`() {
        val box = Geo.boundingBox(59.9386, 30.3141, 1000.0)

        assertThat(distanceMeters(59.9386, 30.3141, box.maxLat, 30.3141)).isCloseTo(1000.0, within(10.0))
        assertThat(distanceMeters(59.9386, 30.3141, 59.9386, box.maxLon)).isCloseTo(1000.0, within(10.0))
        assertThat(box.maxLon - box.minLon).isGreaterThan(box.maxLat - box.minLat)
    }

    @Test
    fun `bounding box is clamped at the poles`() {
        val box = Geo.boundingBox(89.999, 179.999, 5000.0)

        assertThat(box.maxLat).isEqualTo(90.0)
        assertThat(box.maxLon).isEqualTo(180.0)
    }

    @Test
    fun `money is rounded half up to kopecks`() {
        assertThat(Money.of(BigDecimal("2.345"))).isEqualByComparingTo("2.35")
        assertThat(Money.of(BigDecimal("2.344"))).isEqualByComparingTo("2.34")
        assertThat(Money.of(15).scale()).isEqualTo(2)
    }
}
