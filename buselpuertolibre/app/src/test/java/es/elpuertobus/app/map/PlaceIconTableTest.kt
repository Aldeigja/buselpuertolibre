package es.elpuertobus.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaceIconTableTest {
    @Test
    fun listedKindsResolveAndUnknownDoesNot() {
        val style = asset("style.json").readText()
        val sprite = asset("sprite/sprite.json").readText()
        val expected =
            mapOf(
                "pharmacy" to "pharmacy_11",
                "bank" to "bank_11",
                "shop" to "shop_11",
                "restaurant" to "restaurant_11",
                "school" to "school_11",
                "hospital" to "hospital_11",
                "fuel" to "fuel_11",
            )
        expected.forEach { (kind, icon) ->
            assertEquals(icon, iconName(style, kind))
            assertTrue(sprite.contains("\"$icon\""))
        }
        assertNull(iconName(style, "unknown"))
    }

    private fun iconName(style: String, kind: String): String? {
        val layer = layerSlice(style, "places-icon")
        val allowed = quoted(arrayAfter(layer, "filter"))
        if (kind !in allowed) {
            return null
        }
        val tokens = quoted(arrayAfter(layer, "icon-image")).drop(3)
        val pairs = if (tokens.size % 2 == 1) tokens.dropLast(1) else tokens
        return pairs.chunked(2).associate { it[0] to it[1] }[kind]
    }

    private fun layerSlice(style: String, id: String): String {
        val marker = "\"id\": \"$id\""
        val start = style.indexOf(marker)
        require(start >= 0) { marker }
        val next = style.indexOf("\"id\":", start + marker.length)
        return if (next < 0) style.substring(start) else style.substring(start, next)
    }

    private fun arrayAfter(source: String, key: String): String {
        val keyAt = source.indexOf("\"$key\"")
        require(keyAt >= 0) { key }
        val open = source.indexOf('[', keyAt)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(open, index + 1)
                    }
                }
            }
        }
        error("unclosed $key")
    }

    private fun quoted(source: String): List<String> =
        Regex("\"([^\"]+)\"").findAll(source).mapNotNull { it.groups[1]?.value }.toList()

    private fun asset(name: String): File {
        val start = System.getProperty("user.dir") ?: error("user.dir is unset")
        var dir: File? = File(start)
        while (dir != null) {
            val candidates =
                listOf(
                    File(dir, "src/main/assets/$name"),
                    File(dir, "app/src/main/assets/$name"),
                    File(dir, "buselpuertolibre/app/src/main/assets/$name"),
                )
            candidates.firstOrNull { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("missing asset $name from ${System.getProperty("user.dir")}")
    }
}
