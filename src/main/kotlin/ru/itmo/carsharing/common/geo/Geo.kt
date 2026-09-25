package ru.itmo.carsharing.common.geo

import kotlin.math.cos

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/**
 * Прямоугольник вокруг точки для поиска машин рядом: сначала кандидаты отбираются по индексу
 * на (latitude, longitude), потом расстояние уточняется гаверсинусом в SQL.
 */
object Geo {
    private const val METERS_PER_DEGREE_LAT: Double = 111_320.0
    private const val MIN_COS_LAT: Double = 0.01

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
