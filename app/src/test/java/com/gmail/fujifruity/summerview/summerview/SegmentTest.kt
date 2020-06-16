package com.gmail.fujifruity.summerview

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

fun point(azimuth: Double) =
    Point(0.0, 0.0, 0L, 0.0, azimuth, 0.0)

class PointTest {

    @Test
    fun changeOfBearing_crossingNorth_clockwise() {
        val p0 = point(-PI / 4)
        val p1 = point(PI / 4)
        val actual = p1.rotationFrom(p0)
        assertEquals(PI / 2, actual, 1e-8)
    }

    @Test
    fun changeOfBearing_crossingNorth_anticlockwise() {
        val p0 = point(PI / 4)
        val p1 = point(-PI / 4)
        val actual = p1.rotationFrom(p0)
        assertEquals(-PI / 2, actual, 1e-8)
    }

    @Test
    fun changeOfBearing_crossingSouth_clockwise() {
        val p0 = point(4 * PI / 5)
        val p1 = point(6 * PI / 5)
        val actual = p1.rotationFrom(p0)
        assertEquals(2 * PI / 5, actual, 1e-8)
    }

    @Test
    fun changeOfBearing_crossingSouth_anticlockwise() {
        val p0 = point(6 * PI / 5)
        val p1 = point(4 * PI / 5)
        val actual = p1.rotationFrom(p0)
        assertEquals(-2 * PI / 5, actual, 1e-8)
    }

    @Test
    fun changeOfBearing_same() {
        val p0 = point(PI)
        val p1 = point(PI)
        val actual = p1.rotationFrom(p0)
        assertEquals(0.0, actual, 1e-8)
    }

    @Test
    fun changeOfBearing_same_around() {
        val p0 = point(0.0)
        val p1 = point(2 * PI)
        val actual = p1.rotationFrom(p0)
        assertEquals(0.0, actual, 1e-8)
    }

}

fun point(lat: Double, lon: Double) =
    Point(lat, lon, 0L, 0.0, 0.0, 0.0)

fun fp(
    position: Int,
    lat: Int,
    lon: Int,
    time: Int,
    speed: Int,
    bearing: Double,
    accuracy: Int
) =
    Footprint(
        position.toLong(),
        Point(
            lat.toDouble(),
            lon.toDouble(),
            time.toLong(),
            speed.toDouble(),
            bearing,
            accuracy.toDouble()
        )
    )

class FootprintTest {
    fun fp(pos: Int, lat: Int) = fp(pos, lat, 0, 0, 0, 0.0, 0)

    @Test
    fun interpolate_binarySearch() {
        val footprints = listOf(0, 4, 8).map { fp(it, it) }
        footprints.windowed(2).forEach { (f0, f1) ->
            f0.next = f1
            f1.prev = f0
        }
        fun interpolate(keyPositionMs: Long): Footprint? {
            val insertionIdx =
                footprints.binarySearchBy(keyPositionMs) { it.positionMs }.let { insertionIdx ->
                    if (insertionIdx < 0) -(insertionIdx + 1) else insertionIdx
                }
            val idx = insertionIdx.coerceAtMost(footprints.size - 1)
            return footprints[idx].interpolate(keyPositionMs)
        }
        assertEquals(fp(5, 5), interpolate(5))
        assertEquals(fp(0, 0), interpolate(0))
        assertEquals(fp(0, 0), interpolate(-1))
        assertEquals(fp(8, 8), interpolate(8))
        assertEquals(fp(8, 8), interpolate(9))
    }
}

class SegmentTest {

    fun simpleRec(bearing: Double, other: Int) =
        fp(other, other, other, other, other, bearing, other)

    @Test
    fun nearestFootprint_half() {
        val lo1 = fp(0, -1, -1, 0, 0, PI / 6, 0)
        val lo2 = fp(2, 1, 1, 2, 2, -PI / 6, 2)
        val exp = fp(1, 0, 0, 1, 1, 0.0, 1)
        val actual = Segment(lo1, lo2)
            .interpolate(point(0.0, 0.0))
        assertEquals(exp, actual)
    }

    /*
    -1,1  0,1  1,1
    -1,0  0,0  1,0
    -1,-1 0,-1 1,-1
     */

    @Test
    fun nearestFootprint_quoter() {
        val lo1 = fp(0, 0, 0, 0, 0, 0.0, 0)
        val lo2 = fp(4, 4, 4, 4, 4, PI / 2, 4)
        val exp = fp(1, 1, 1, 1, 1, PI / 8, 1)
        val actual = Segment(lo1, lo2)
            .interpolate(point(0.0, 2.0))
        assertEquals(exp, actual)
    }

    /*
    0 0 0 0 0
    0 0 0 0 0
    0 0 0 0 0
    0 0 0 0 0
    0 0 0 0 0
     */


    @Test
    fun nearestFootprint_v1() {
        val lo1 = fp(0, 0, 0, 0, 0, PI / 2, 10)
        val lo2 = fp(1, 1, 1, 1, 1, PI / 3, 350)
        val actual = Segment(lo1, lo2)
            .interpolate(point(0.0, -1.0))
        assertEquals(lo1, actual)
    }

    /*
    -1,1  0,1  1,1
    -1,0  0,0  1,0
    -1,-1 0,-1 1,-1
     */

    @Test
    fun nearestFootprint_v2WhenSameVec() {
        val lo1 = fp(0, 0, 0, 0, 0, 0.0, 10)
        val lo2 = fp(2, 0, 0, 1, 1, PI, 350)
        val actual = Segment(lo1, lo2).interpolate(lo1)
        assertEquals(lo2, actual)
    }

    /*
    -1, 1 0, 1 1, 1
    -1, 0 0, 0 1, 0
    -1,-1 0,-1 1,-1
     */

    @Test
    fun sqdist_toMidPoint() {
        val lo1 = fp(0, -1, -1, 0, 0, 0.0, 0)
        val lo2 = fp(0, 1, 1, 0, 0, 0.0, 0)
        val actual = Segment(lo1, lo2)
            .sqDist(point(-1.0, 1.0))
        assertEquals(2.0, actual, 1e-7)
    }

    @Test
    fun sqdist_ToV1() {
        val lo1 = fp(0, 0, 0, 0, 0, 0.0, 0)
        val lo2 = fp(0, 1, 1, 0, 0, 0.0, 0)
        val actual = Segment(lo1, lo2)
            .sqDist(point(-1.0, 1.0))
        assertEquals(2.0, actual, 1e-7)
    }

    @Test
    fun sqdist_zeroDistWhenEqualsV1() {
        val lo1 = fp(0, 0, 0, 0, 0, 0.0, 0)
        val lo2 = fp(0, 1, 1, 0, 0, 0.0, 0)
        val actual = Segment(lo1, lo2)
            .sqDist(point(0.0, 0.0))
        assertEquals(0.0, actual, 1e-7)
    }

    /*
    -1, 1 0, 1 1, 1
    -1, 0 0, 0 1, 0
    -1,-1 0,-1 1,-1
     */

    @Test
    fun interpolatedFootprint_3rdQuater_bearingAnticlockwise() {
        val lo1 = fp(0, 0, 0, 0, 0, PI / 4, 0)
        val lo2 = fp(4, 4, 4, 4, 4, -PI / 4, 4)
        val exp = fp(3, 3, 3, 3, 3, -PI / 8, 3)
        val actual = Segment(lo1, lo2).interpolate(3)
        assertEquals(exp, actual)
    }

    @Test
    fun interpolatedFootprint_v1WhenScaleIsZero() {
        val lo1 = fp(0, 0, 0, 0, 0, 0.0, 0)
        val lo2 = fp(1, 1, 1, 1, 1, 0.0, 1)
        val actual = Segment(lo1, lo2).interpolate(0)
        assertEquals(lo1, actual)
    }


}
