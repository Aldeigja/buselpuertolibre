package es.elpuertobus.app.map

import android.content.Context
import java.io.File

/**
 * MapLibre 11.8 reads PMTiles with byte ranges. APK assets do not support those
 * ranges, so the archive is copied to app files and opened with a file URL.
 */
fun localBasemapStyleJson(context: Context): String {
    val archive = File(context.filesDir, "el_puerto.pmtiles")
    context.assets.openFd("el_puerto.pmtiles").use { descriptor ->
        if (!archive.exists() || archive.length() != descriptor.length) {
            context.assets.open("el_puerto.pmtiles").use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
    val style = context.assets.open("style.json").bufferedReader().use { it.readText() }
    return style.replace(
        "pmtiles://asset://el_puerto.pmtiles",
        "pmtiles://file://${archive.absolutePath}",
    )
}
