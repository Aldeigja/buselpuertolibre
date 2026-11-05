package es.elpuertobus.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStripesTest {
    @Test
    fun twoLinesUseOppositeSidesOfTheCentre() {
        val segment = eastbound()
        val stripes = RouteStripes.build(lines(1, 2, segment = segment))
        val byLine = stripes.associate { it.line to it.offsetPx }
        assertEquals(-RouteStripes.OFFSET_STEP_PX / 2.0, byLine.getValue(1), 0.001)
        assertEquals(RouteStripes.OFFSET_STEP_PX / 2.0, byLine.getValue(2), 0.001)
    }

    @Test
    fun threeAndFourLinesKeepLineNumberOrder() {
        val segment = eastbound()
        val three =
            RouteStripes.build(lines(1, 3, 5, segment = segment))
                .associate { it.line to it.offsetPx }
        assertEquals(-RouteStripes.OFFSET_STEP_PX, three.getValue(1), 0.001)
        assertEquals(0.0, three.getValue(3), 0.001)
        assertEquals(RouteStripes.OFFSET_STEP_PX, three.getValue(5), 0.001)

        val four =
            RouteStripes.build(lines(1, 2, 3, 4, segment = segment))
                .associate { it.line to it.offsetPx }
        assertEquals(-1.5 * RouteStripes.OFFSET_STEP_PX, four.getValue(1), 0.001)
        assertEquals(-0.5 * RouteStripes.OFFSET_STEP_PX, four.getValue(2), 0.001)
        assertEquals(0.5 * RouteStripes.OFFSET_STEP_PX, four.getValue(3), 0.001)
        assertEquals(1.5 * RouteStripes.OFFSET_STEP_PX, four.getValue(4), 0.001)
    }

    @Test
    fun fiveLinesStayVisibleInLineNumberOrder() {
        val stripes =
            RouteStripes.build(lines(1, 2, 4, 5, 7, segment = eastbound()))
                .associate { it.line to it.offsetPx }
        assertEquals(-2 * RouteStripes.OFFSET_STEP_PX, stripes.getValue(1), 0.001)
        assertEquals(-RouteStripes.OFFSET_STEP_PX, stripes.getValue(2), 0.001)
        assertEquals(0.0, stripes.getValue(4), 0.001)
        assertEquals(RouteStripes.OFFSET_STEP_PX, stripes.getValue(5), 0.001)
        assertEquals(2 * RouteStripes.OFFSET_STEP_PX, stripes.getValue(7), 0.001)
    }

    @Test
    fun aReversedCopyStaysOnTheSameSideOfTheStreet() {
        val segment = eastbound()
        val stripes =
            RouteStripes.build(
                listOf(
                    RoutePath(1, "#111111", listOf(segment)),
                    RoutePath(2, "#222222", listOf(segment.asReversed())),
                ),
            )
        val line1 = stripes.single { it.line == 1 }
        val line2 = stripes.single { it.line == 2 }
        assertEquals(-RouteStripes.OFFSET_STEP_PX / 2.0, line1.offsetPx, 0.001)
        assertEquals(-RouteStripes.OFFSET_STEP_PX / 2.0, line2.offsetPx, 0.001)
        assertTrue(line1.points.first()[0] < line1.points.last()[0])
        assertTrue(line2.points.first()[0] > line2.points.last()[0])
    }

    @Test
    fun aSharpBendIsSplitSoTheCornerCannotSpike() {
        val hairpin =
            listOf(
                doubleArrayOf(-6.2320, 36.6000),
                doubleArrayOf(-6.2300, 36.6000),
                doubleArrayOf(-6.2320, 36.5990),
            )
        val stripes =
            RouteStripes.build(
                listOf(RoutePath(4, "#ff0000", listOf(hairpin))),
            )
        assertEquals(2, stripes.size)
        assertTrue(stripes.all { it.points.size == 2 })
    }

    @Test
    fun aGentleBendStaysOneStroke() {
        val bend =
            listOf(
                doubleArrayOf(-6.2300, 36.6000),
                doubleArrayOf(-6.2290, 36.6000),
                doubleArrayOf(-6.2280, 36.6003),
            )
        val stripes =
            RouteStripes.build(
                listOf(RoutePath(6, "#95e137", listOf(bend))),
            )
        assertEquals(1, stripes.size)
        assertEquals(3, stripes.single().points.size)
        assertEquals(0.0, stripes.single().offsetPx, 0.001)
    }

    @Test
    fun aLineIsDrawnOnceWhenItsReturnRetracesTheSameStreet() {
        val segment = eastbound()
        val stripes =
            RouteStripes.build(
                listOf(
                    RoutePath(6, "#95e137", listOf(segment, segment.asReversed())),
                ),
            )
        assertEquals(1, stripes.size)
    }

    @Test
    fun touchingPartsOfOneLineBecomeOneStroke() {
        val west =
            listOf(
                doubleArrayOf(-6.2310, 36.6000),
                doubleArrayOf(-6.2300, 36.6000),
            )
        val east =
            listOf(
                doubleArrayOf(-6.2300, 36.6000),
                doubleArrayOf(-6.2290, 36.6000),
            )
        val stripes =
            RouteStripes.build(
                listOf(RoutePath(4, "#ff0000", listOf(east, west))),
            )
        assertEquals(1, stripes.size)
        assertEquals(3, stripes.single().points.size)
    }

    private fun eastbound(): List<DoubleArray> =
        listOf(
            doubleArrayOf(-6.2300, 36.6000),
            doubleArrayOf(-6.2290, 36.6000),
        )

    private fun lines(vararg lineNumbers: Int, segment: List<DoubleArray>): List<RoutePath> =
        lineNumbers.map { line -> RoutePath(line, "#000000", listOf(segment)) }
}
