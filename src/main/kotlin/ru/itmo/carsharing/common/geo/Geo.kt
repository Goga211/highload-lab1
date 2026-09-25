package ru.itmo.carsharing.common.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/**
 * Геометрия на сфере для поиска машин рядом и определения зоны.
 * Сначала отбираем кандидатов прямоугольником по индексу, потом уточняем гаверсинусом.
 */
object Geo {
    const val EARTH_RADIUS_M: Double = 6_371_000.0
    private const val METERS_PER_DEGREE_LAT: Double = 111_320.0
    private const val MIN_COS_LAT: Double = 0.01

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    fun boundingBox(lat: Double, lon: Double, radiusM: Double): BoundingBox {
        val dLat = radiusM / METERS_PER_DEGREE_LAT
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(MIN_COS_LAT)
        val dLon = radiusM / (METERS_PER_DEGREE_LAT * cosLat)
        return BoundingBox(
            minLat = (lat - dLat).coerceAtLeast(-90.0),
            maxLat = (lat + dLat).coerceAtMost(90.0),
            minLon = (lon - dLon).coerceAtLeast(-180.0),
            maxLon = (lon + dLon).coerceAtMost(180.0),
        )
    }
}
