package com.gmail.fujifruity.summerview

import org.junit.Assert
import org.junit.Test
import kotlin.math.PI

fun point(x: Int, y: Int, rad: Double) = point(x, y, rad, 0)
fun point(x: Int, y: Int, rad: Double, spd: Int) =
    Point(
        x.toDouble() / 111000,
        y.toDouble() / 111000,
        0L,
        spd.toDouble(),
        rad,
        0.0
    )

class TreeNodeTest {

    fun test_findSimilarNearest(map: String, expected: Point?, targetSpeed: Int = 0) {
        val chars = map.trimIndent().lines().map { it.toCharArray().toList() }
        val points = mutableListOf<Point>()
        var target: Point? = null
        for (y in chars.indices)
            for (x in chars[0].indices) {
                when (chars[y][x]) {
                    'n' -> points.add(point(x, y, 0.0))
                    'e' -> points.add(point(x, y, PI / 2))
                    's' -> points.add(point(x, y, PI))
                    'w' -> points.add(point(x, y, -PI / 2))
                    'x' -> target = point(x, y, 0.0, targetSpeed)
                }
            }
        val tree = TreeNode.create(points)
        val nearest = tree.findSimilarNearest(target!!)
        Assert.assertEquals(expected, nearest)
    }

    @Test
    fun findSimilarNearest_single() {
        val p = point(0, 0, 0.0)
        val tree = TreeNode.create(listOf(p))
        val nearest = tree.findSimilarNearest(p)
        Assert.assertEquals(p, nearest)
    }

    @Test
    fun findSimilarNearest_right() {
        val map = """
        n.x
        nnn
        nnn
        """
        test_findSimilarNearest(map, point(2, 1, 0.0))
    }

    @Test
    fun findSimilarNearest_above() {
        val map = """
        nnn
        nnn
        n.x
        """
        test_findSimilarNearest(map, point(2, 1, 0.0))
    }

    @Test
    fun findSimilarNearest_bottom() {
        val map = """
        n.n
        .x.
        nnn
        """
        test_findSimilarNearest(map, point(1, 2, 0.0))
    }

    @Test
    fun findSimilarNearest_takeBottomLeftByBearing() {
        val map = """
        .wx
        .es
        n..
        """
        test_findSimilarNearest(map, point(0, 2, 0.0))
    }

    @Test
    fun findSimilarNearest_refuseByBearing() {
        val map = """
        e.w
        .s.
        e.x
        """
        test_findSimilarNearest(map, null)
    }

    @Test
    fun findSimilarNearest_refuseBySpeed() {
        val map = """
        n..............x
        """
        test_findSimilarNearest(map, null)
    }

    @Test
    fun findSimilarNearest_acceptBySpeed() {
        val map = """
        n..............x
        """
        test_findSimilarNearest(map, point(0, 0, 0.0), 10)
    }

}
