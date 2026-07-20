package org.opentripplanner.trakpi.otp.testset.transforms

import kotlin.math.cos

/** A geographic point. */
data class Coordinate(val latitude: Double, val longitude: Double)

/** Maps a coordinate to the coordinate of the nearest transit station. */
fun interface CoordinateSnapper {
    fun snap(coordinate: Coordinate): Coordinate
}

/**
 * An in-memory nearest-station lookup over a fixed set of [stations]. Because the whole set is held in
 * memory, there is always a nearest station. No radius or fallback is needed.
 */
class StationIndex(private val stations: List<Coordinate>) : CoordinateSnapper {
    init {
        require(stations.isNotEmpty()) { "StationIndex needs at least one station." }
    }

    /** The coordinate of the station nearest to [coordinate], by equirectangular distance. */
    override fun snap(coordinate: Coordinate): Coordinate = stations.minByOrNull { squaredDistance(it, coordinate) }!!

    private fun squaredDistance(a: Coordinate, b: Coordinate): Double {
        val meanLat = Math.toRadians((a.latitude + b.latitude) / 2)
        val dLat = a.latitude - b.latitude
        val dLon = (a.longitude - b.longitude) * cos(meanLat)
        return dLat * dLat + dLon * dLon
    }
}
