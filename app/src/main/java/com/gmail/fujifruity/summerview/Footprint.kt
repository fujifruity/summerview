package com.gmail.fujifruity.summerview

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import java.io.Serializable
import java.util.*
import kotlin.math.*

interface Locative {
    companion object {
        val degreeInMeter = 111000.0
    }

    val latitude: Double
    val longitude: Double
    val azimuth: Double
    val latLng: LatLng
        get() = LatLng(latitude, longitude)

    fun sqDist(that: Locative): Double {
        val dLat = that.latitude - latitude
        // At higher latitudes, the distance of one degree of longitude decreases.
        val scale = cos(that.latitude * PI / 180)
        val dLon = scale * (that.longitude - longitude)
        return dLat * dLat + dLon * dLon
    }

    /**
     * @return [-PI, PI]
     */
    fun rotationFrom(from: Locative): Double {
        val travel = azimuth - from.azimuth
        return atan2(sin(travel), cos(travel))
    }

    fun distMeter(that: Locative): Double {
        return sqrt(sqDist(that)) * degreeInMeter
    }

}

data class Vector(val x: Double, val y: Double) {

    constructor(l: Locative) : this(l.latitude, l.longitude)

    /**
     * When [azimuthRadian] is 0.0, returns a north-pointing unit Vector, which is x=1.0 and y=0.0.
     * When [azimuthRadian] is PI/2, returns a east-pointing unit Vector, which is x=0.0 and y=1.0.
     */
    constructor(azimuthRadian: Double) : this(cos(azimuthRadian), sin(azimuthRadian))

    fun add(v: Vector) =
        Vector(x + v.x, y + v.y)

    fun sub(v: Vector) =
        Vector(x - v.x, y - v.y)

    fun dot(v: Vector): Double = x * v.x + y * v.y
    fun sqDist(v: Vector): Double {
        val dx = v.x - x
        val dy = v.y - y
        return dx * dx + dy * dy
    }

    fun sqnorm(): Double = x * x + y * y
    fun scale(c: Double) = Vector(c * x, c * y)
    fun scalarProjection(w: Vector): Double {
        // Let v=this, the projection of w onto v as cv (c: Double).
        // Since w-cv is perpendicular to v,
        // (w - cv).v == 0
        // w.v - c|v|^2 == 0
        // w.v / |v|^2 == c
        return w.dot(this) / this.sqnorm()
    }
}

/**
 * @param latitude `[-90.0, 90.0]`
 * @param longitude `[-180.0, 180.0]`
 * @param elapsedRealtime elapsed real-time since system boot in ms.
 * @param speed m/s calculated from previous point.
 * @param rawAzimuth direction of travel from previous point in radian. 0=north, PI/2=east.
 * @param accuracy 68% confidence area, radial, in meters.
 */
data class Point(
    override val latitude: Double,
    override val longitude: Double,
    val elapsedRealtime: Long,
    val speed: Double,
    private val rawAzimuth: Double,
    val accuracy: Double
) : Locative, Serializable {

    override val azimuth = rawAzimuth.let { rad ->
        if (rad.absoluteValue <= PI) rad else atan2(sin(rad), cos(rad))
    }

    init {
        require(latitude.absoluteValue <= 90.0) { "latitude: $latitude is out of range." }
        require(longitude.absoluteValue <= 180.0) { "longitude: $longitude is out of range." }
    }

    override fun toString(): String {
        return "Point(lat=%.5f, lon=%.5f, spd=%+.2fm/s, azi=%.2frad, accuracy=%.2fm, time=%d)".format(
            latitude, longitude, speed, azimuth, accuracy, elapsedRealtime
        )
    }

    fun isSimilar(l: Locative): Boolean {
        val toleranceMeter = speed.absoluteValue + TOLERANCE_METER
        val rotation = rotationFrom(l).absoluteValue
        return distMeter(l) < toleranceMeter && rotation < TOLERANCE_RAD
    }

    fun thresholdMeter() = 0.2 * accuracy
//    fun thresholdMeter() = Math.max(0.51, -0.25 + 0.206 * accuracy)

    fun isMoving(): Boolean = speed >= thresholdMeter()

    companion object {
        private const val TOLERANCE_METER = 10
        private const val TOLERANCE_RAD = 1.0
        private const val serialVersionUID = 1L

        private fun createNaively(location: Location): Point =
            Point(
                location.latitude,
                location.longitude,
                location.elapsedRealtimeNanos / 1000_000,
                location.speed.toDouble(),
                (location.bearing * PI / 180).let { rad ->
                    if (rad.absoluteValue <= PI) rad else atan2(sin(rad), cos(rad))
                },
                location.accuracy.toDouble()
            )

        /**
         * We don't trust android.location.Location.speed. Instead, calculate them manually.
         */
        fun from(liveL: Location, prevL: Location?, prevP: Point?): Point {
            val naiveLive = createNaively(liveL)
            return if (prevP == null) {
                naiveLive
            } else {
                // Calculate speed from prev.
                val deltaMs = naiveLive.elapsedRealtime - prevP.elapsedRealtime
                val speed = liveL.distanceTo(prevL) * 1000.0 / deltaMs
                Point(
                    liveL.latitude,
                    liveL.longitude,
                    naiveLive.elapsedRealtime,
                    speed,
                    naiveLive.azimuth,
                    naiveLive.accuracy
                )
            }
        }

    }

}

/**
 * @param positionMs Milli seconds from the beginning of the recording
 */
class Footprint(
    val positionMs: Long,
    val point: Point,
    var prev: Footprint? = null,
    var next: Footprint? = null
) : Locative by point, Serializable {

    private val prevSegment
        get() = prev?.let { Segment(it, this) }
    private val nextSegment
        get() = next?.let { Segment(this, it) }

    override fun toString(): String {
        // if Footprint was a data class, prev and next will causes stack overflow
        return "Footprint(pos=$positionMs, point=$point)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Footprint) return false
        return this.positionMs == other.positionMs && this.point == other.point
    }

    override fun hashCode(): Int {
        return Objects.hash(positionMs, point)
    }

    /**
     * ```
     *      that
     *       |
     * prev--x--this----next
     * => return 'x'
     *
     *                       that
     * prev----this----next
     * => return 'next'
     * ```
     */
    fun interpolate(that: Locative): Footprint? {
        return when ((prev != null) to (next != null)) {
            true to true -> {
                val closerSegment = listOf(prevSegment!!, nextSegment!!).minBy { it.sqDist(that) }!!
                closerSegment.interpolate(that)
            }
            true to false -> prevSegment!!.interpolate(that)
            false to true -> nextSegment!!.interpolate(that)
            else -> this
        }
    }

    fun interpolate(positionMs: Long): Footprint {
        return when {
            this.positionMs < positionMs -> nextSegment?.interpolate(positionMs) ?: this
            positionMs < this.positionMs -> prevSegment?.interpolate(positionMs) ?: this
            else -> this
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

class Segment(val start: Footprint, val end: Footprint) {
    private val va = Vector(start)
    private val vb = Vector(end)
    private val p1 = start.point
    private val p2 = end.point

    fun sqDist(that: Locative): Double {
        val vx = Vector(that)
        if (va == vb) return va.sqDist(vx)
        val vab = vb.sub(va)
        val vax = vx.sub(va)
        val c = vab.scalarProjection(vax)
        // The projection of w onto v
        val pointOnThisSegment =
            if (c > 1) vb else if (c < 0) va else va.add(vab.scale(c))
        return vx.sqDist(pointOnThisSegment)
    }

    /** Creates interpolated footprint on this segment according to the locative. */
    fun interpolate(that: Locative): Footprint {
        val vx = Vector(that)
        if (va == vb) return end
        val vab = vb.sub(va)
        val vax = vx.sub(va)
        val c = vab.scalarProjection(vax)
        return when {
            c > 1 -> end
            c < 0 -> start
            else -> {
                val interpolatedMsec = start.positionMs + c * (end.positionMs - start.positionMs)
                interpolate(interpolatedMsec.toLong())
            }
        }
    }

    /** Creates interpolated footprint on this segment according to the position. */
    fun interpolate(keyPositionMs: Long): Footprint {
        require(start.positionMs <= keyPositionMs || keyPositionMs <= end.positionMs) {
            "param: $keyPositionMs, out of [${start.positionMs}, ${end.positionMs}]."
        }
        if (va == vb) return end
        val scale =
            (keyPositionMs.toDouble() - start.positionMs) / (end.positionMs - start.positionMs)
        val vab = vb.sub(va)
        val interVec = va.add(vab.scale(scale))
        val interTime = p1.elapsedRealtime + scale * (p2.elapsedRealtime - p1.elapsedRealtime)
        val interSpeed = p1.speed + scale * (p2.speed - p1.speed)
        val interAccuracy = p1.accuracy + scale * (p2.accuracy - p1.accuracy)
        val interBearing = p1.azimuth + p2.rotationFrom(p1) * scale
        return Footprint(
            keyPositionMs,
            Point(
                interVec.x,
                interVec.y,
                interTime.toLong(),
                interSpeed,
                interBearing,
                interAccuracy
            )
        )
    }

}

class TreeNode<T : Locative>(
    val node: T,
    val left: TreeNode<T>?,
    val right: TreeNode<T>?,
    val rect: Rectangle
) : Serializable {

    fun count(): Int =
        if (left == null && right == null) 1 else 1 + (left?.count() ?: 0) + (right?.count() ?: 0)

    fun findSimilarNearest(target: Point): T? {
        var (bestPoint, bestSqDist) =
            if (target.isSimilar(node)) node to node.sqDist(target)
            else null to Double.POSITIVE_INFINITY

        fun _findSimilarNearest(tree: TreeNode<T>) {
            // Update bestNode.
            val sqDist = tree.node.sqDist(target)
            if (target.isSimilar(tree.node) && sqDist < bestSqDist) {
                bestPoint = tree.node
                bestSqDist = sqDist
            }
            // Go deeper if necessary.
            listOf(tree.left, tree.right).forEach {
                if (it != null && it.rect.sqDist(target) < bestSqDist) {
                    _findSimilarNearest(it)
                }
            }
        }

        _findSimilarNearest(this)
        return bestPoint
    }

    class Rectangle(val sw: Locative, val ne: Locative) : Serializable {
        init {
            require(sw.latitude <= ne.latitude) { "lat: ${sw.latitude} > ${ne.latitude}" }
            require(sw.longitude <= ne.longitude) { "lon: ${sw.longitude} > ${ne.longitude}" }
        }

        fun sqDist(l: Locative): Double {
            val nearest =
                location(
                    l.latitude.coerceIn(sw.latitude, ne.latitude),
                    l.longitude.coerceIn(sw.longitude, ne.longitude)
                )
            return l.sqDist(nearest)
        }

        fun splitIntoWestAndEast(l: Locative) = Pair(
            Rectangle(sw, location(ne.latitude, l.longitude)),
            Rectangle(location(sw.latitude, l.longitude), ne)
        )

        fun splitIntoSouthAndNorth(l: Locative) = Pair(
            Rectangle(sw, location(l.latitude, ne.longitude)),
            Rectangle(location(l.latitude, sw.longitude), ne)
        )

        companion object {
            private const val serialVersionUID = 1L

            private fun location(lat: Double, lon: Double) =
                Point(
                    lat,
                    lon,
                    0L,
                    0.0,
                    0.0,
                    0.0
                )

            fun from(points: List<Locative>): Rectangle {
                var latMax = -90.0
                var lonMax = -180.0
                var latMin = 90.0
                var lonMin = 180.0
                for (p in points) {
                    latMax = max(latMax, p.latitude)
                    lonMax = max(lonMax, p.longitude)
                    latMin = min(latMin, p.latitude)
                    lonMin = min(lonMin, p.longitude)
                }
                return Rectangle(location(latMin, lonMin), location(latMax, lonMax))
            }
        }
    }

    companion object {
        private val TAG = TreeNode::class.java.simpleName
        private val K = 2
        private const val serialVersionUID = 1L

        fun <T : Locative> create(locatives: List<T>): TreeNode<T> {
            require(locatives.isNotEmpty()) { "points is empty" }
            return _create(
                locatives,
                0,
                Rectangle.from(locatives)
            )!!
        }

        private fun <T : Locative> _create(
            points: List<T>?,
            axis: Int,
            rect: Rectangle
        ): TreeNode<T>? {

            if (points == null || points.isEmpty()) return null

            // Pick out the median-of-three.
            val midIdx = points.size / 2
            val three = listOf(
                Pair(0, points.first()),
                Pair(midIdx, points[midIdx]),
                Pair(points.size - 1, points.last())
            )
            val (medianIdx, median) = when (axis) {
                0 -> three.sortedBy { it.second.latitude }[1]
                1 -> three.sortedBy { it.second.longitude }[1]
                else -> throw Exception("Invalid axis.")
            }
            val points = points.toMutableList()
            points.removeAt(medianIdx)

            // Create hyper rectangles and grouped locatives
            val (leftRect, rightRect) = when (axis) {
                0 -> rect.splitIntoSouthAndNorth(median)
                1 -> rect.splitIntoWestAndEast(median)
                else -> throw Exception("Invalid axis.")
            }
            val (leftPoints, rightPoints) = when (axis) {
                0 -> points.groupBy { it.latitude < median.latitude }
                1 -> points.groupBy { it.longitude < median.longitude }
                else -> throw Exception("Invalid axis.")
            }.let { Pair(it[true], it[false]) }
            val axis = (axis + 1) % K
            val left = _create(leftPoints, axis, leftRect)
            val right = _create(rightPoints, axis, rightRect)

            return TreeNode(median, left, right, rect)
        }
    }
}

