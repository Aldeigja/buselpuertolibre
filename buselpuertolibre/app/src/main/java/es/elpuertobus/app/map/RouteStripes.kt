package es.elpuertobus.app.map

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

data class RoutePath(
    val line: Int,
    val colour: String,
    val parts: List<List<DoubleArray>>,
)

data class RouteStripe(
    val line: Int,
    val colour: String,
    val points: List<DoubleArray>,
    val offsetPx: Double,
)

/**
 * Side-by-side ribbons for shared streets. The offset is a screen distance, applied
 * later by the map as line-offset, so it is computed once and does not follow the camera.
 * A bend of 90 degrees or more starts a new piece, so the map never miters that corner.
 */
object RouteStripes {
    const val OFFSET_STEP_PX = 4.0
    private const val SHARP_TURN_DEGREES = 90.0

    fun build(routes: List<RoutePath>): List<RouteStripe> {
        val prepared = routes.map { route -> route.copy(parts = stitch(route.parts)) }
        val occupancy = HashMap<Edge, java.util.TreeSet<Int>>()
        for (route in prepared) {
            for (part in route.parts) {
                eachEdge(part) { edge, _, _ ->
                    occupancy.getOrPut(edge) { java.util.TreeSet() }.add(route.line)
                }
            }
        }
        val drawn = HashSet<Pair<Int, Edge>>()
        val stripes = ArrayList<RouteStripe>()
        for (route in prepared.sortedBy { it.line }) {
            for (part in route.parts) {
                var open: MutableList<DoubleArray>? = null
                var openOffset = 0.0
                var incoming = Double.NaN
                fun flush() {
                    val points = open
                    if (points != null && points.size >= 2) {
                        stripes.add(
                            RouteStripe(route.line, route.colour, points.toList(), openOffset),
                        )
                    }
                    open = null
                    incoming = Double.NaN
                }
                eachEdge(part) { edge, start, end ->
                    if (!drawn.add(route.line to edge)) {
                        flush()
                        return@eachEdge
                    }
                    val members = occupancy.getValue(edge).toList()
                    val slot = members.indexOf(route.line)
                    val base = (slot - (members.size - 1) / 2.0) * OFFSET_STEP_PX
                    val offset = if (edge.forward(start, end)) base else -base
                    val heading = bearing(start, end)
                    val current = open
                    val sharp =
                        current != null &&
                            !incoming.isNaN() &&
                            deflection(incoming, heading) >= SHARP_TURN_DEGREES
                    if (
                        current != null &&
                        offset == openOffset &&
                        !sharp &&
                        near(current.last(), start, 1.0)
                    ) {
                        current.add(end.copyOf())
                    } else {
                        flush()
                        open = mutableListOf(start.copyOf(), end.copyOf())
                        openOffset = offset
                    }
                    incoming = heading
                }
                flush()
            }
        }
        return stripes
    }

    private fun stitch(parts: List<List<DoubleArray>>): List<List<DoubleArray>> {
        val chains = ArrayList<MutableList<DoubleArray>>()
        for (part in parts) {
            val cleaned = ArrayList<DoubleArray>()
            for (point in part) {
                if (cleaned.isEmpty() || !near(cleaned.last(), point, 0.5)) {
                    cleaned.add(point.copyOf())
                }
            }
            if (cleaned.size >= 2) {
                chains.add(cleaned)
            }
        }
        var merged = true
        while (merged) {
            merged = false
            var left = 0
            while (left < chains.size && !merged) {
                var right = left + 1
                while (right < chains.size) {
                    val joined = join(chains[left], chains[right])
                    if (joined != null) {
                        chains[left] = joined
                        chains.removeAt(right)
                        merged = true
                        break
                    }
                    right += 1
                }
                left += 1
            }
        }
        return chains
    }

    private fun join(
        left: List<DoubleArray>,
        right: List<DoubleArray>,
    ): MutableList<DoubleArray>? {
        if (near(left.last(), right.first(), 1.5)) {
            return combine(left, right)
        }
        if (near(left.last(), right.last(), 1.5)) {
            return combine(left, right.asReversed())
        }
        if (near(left.first(), right.last(), 1.5)) {
            return combine(right, left)
        }
        if (near(left.first(), right.first(), 1.5)) {
            return combine(right.asReversed(), left)
        }
        return null
    }

    private fun combine(
        head: List<DoubleArray>,
        tail: List<DoubleArray>,
    ): MutableList<DoubleArray> {
        val points = ArrayList<DoubleArray>(head.size + tail.size)
        points.addAll(head)
        for (point in tail.drop(1)) {
            if (!near(points.last(), point, 0.5)) {
                points.add(point)
            }
        }
        return points
    }

    private fun eachEdge(
        part: List<DoubleArray>,
        block: (Edge, DoubleArray, DoubleArray) -> Unit,
    ) {
        for (index in 0 until part.lastIndex) {
            val start = part[index]
            val end = part[index + 1]
            val edge = Edge.of(start, end) ?: continue
            block(edge, start, end)
        }
    }

    private fun bearing(start: DoubleArray, end: DoubleArray): Double {
        val lat = Math.toRadians((start[1] + end[1]) / 2.0)
        val east = (end[0] - start[0]) * cos(lat)
        val north = end[1] - start[1]
        return Math.toDegrees(atan2(east, north))
    }

    private fun deflection(incoming: Double, outgoing: Double): Double {
        var turn = abs(outgoing - incoming) % 360.0
        if (turn > 180.0) {
            turn = 360.0 - turn
        }
        return turn
    }

    private fun near(a: DoubleArray, b: DoubleArray, meters: Double): Boolean {
        val lat = Math.toRadians((a[1] + b[1]) / 2.0)
        val east = (b[0] - a[0]) * 111_320.0 * cos(lat)
        val north = (b[1] - a[1]) * 110_540.0
        return hypot(east, north) <= meters
    }

    private data class Edge(val ax: Int, val ay: Int, val bx: Int, val by: Int) {
        fun forward(start: DoubleArray, end: DoubleArray): Boolean {
            val sx = quantize(start[0])
            val sy = quantize(start[1])
            return sx == ax && sy == ay
        }

        companion object {
            fun of(start: DoubleArray, end: DoubleArray): Edge? {
                val ax = quantize(start[0])
                val ay = quantize(start[1])
                val bx = quantize(end[0])
                val by = quantize(end[1])
                if (ax == bx && ay == by) {
                    return null
                }
                return if (ax < bx || (ax == bx && ay <= by)) {
                    Edge(ax, ay, bx, by)
                } else {
                    Edge(bx, by, ax, ay)
                }
            }
        }
    }

    private fun quantize(value: Double): Int = (value * 100_000.0).roundToInt()
}
