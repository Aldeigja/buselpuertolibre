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
    val fonts = copyFontAssets(context)
    val sprite = copySpriteAssets(context)
    val style = context.assets.open("style.json").bufferedReader().use { it.readText() }
    return style
        .replace(
            "pmtiles://asset://el_puerto.pmtiles",
            "pmtiles://file://${archive.absolutePath}",
        )
        .replace(
            "asset://fonts/{fontstack}/{range}.pbf",
            "file://${fonts.absolutePath}/{fontstack}/{range}.pbf",
        )
        .replace(
            "asset://sprite/sprite",
            "file://${sprite.absolutePath}/sprite",
        )
}

private fun copySpriteAssets(context: Context): File {
    val dest = File(context.filesDir, "sprite")
    dest.mkdirs()
    listOf("sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png").forEach { name ->
        val out = File(dest, name)
        val bytes = context.assets.open("sprite/$name").use { it.readBytes() }
        if (!out.exists() || out.length() != bytes.size.toLong()) {
            out.writeBytes(bytes)
        }
    }
    return dest
}

private fun copyFontAssets(context: Context): File {
    val dest = File(context.filesDir, "fonts")
    val stacks = context.assets.list("fonts").orEmpty()
    for (stack in stacks) {
        val outDir = File(dest, stack)
        outDir.mkdirs()
        val files = context.assets.list("fonts/$stack").orEmpty()
        for (name in files) {
            val out = File(outDir, name)
            val bytes = context.assets.open("fonts/$stack/$name").use { it.readBytes() }
            if (out.exists() && out.length() == bytes.size.toLong()) {
                continue
            }
            out.writeBytes(bytes)
        }
    }
    return dest
}
