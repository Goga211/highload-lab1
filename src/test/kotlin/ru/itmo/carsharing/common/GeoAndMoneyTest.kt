package ru.itmo.carsharing.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.common.geo.Geo
import ru.itmo.carsharing.common.money.Money
import java.math.BigDecimal

class GeoAndMoneyTest {

    @Test
    fun `distance between city center and airport is about 15 km`() {
        val distance = Geo.distanceMeters(59.9386, 30.3141, 59.8003, 30.2625)

        assertThat(distance).isCloseTo(15_650.0, within(300.0))
    }

    @Test
    fun `distance to the same point is zero`() {
        assertThat(Geo.distanceMeters(59.9, 30.3, 59.9, 30.3)).isZero()
    }

    @Test
    fun `bounding box contains every point of the circle`() {
        val box = Geo.boundingBox(59.9386, 30.3141, 1000.0)

        assertThat(box.minLat).isLessThan(59.9386).isGreaterThan(59.92)
        assertThat(box.maxLon - box.minLon).isGreaterThan(box.maxLat - box.minLat)
        assertThat(Geo.distanceMeters(59.9386, 30.3141, box.maxLat, 30.3141)).isCloseTo(1000.0, within(10.0))
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
