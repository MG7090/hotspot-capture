package com.example.hotspotcapture

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CapturedAsset(
    val url: String,
    val mime: String,
    val file: File,
    val size: Long,
    val type: String
)

class AssetRepository(private val ctx: Context) {

    val assets = mutableListOf<CapturedAsset>()
    private val dir = File(ctx.filesDir, "capture").apply { mkdirs() }
    private val seen = mutableSetOf<String>()

    @Synchronized
    fun save(url: String, mime: String, bytes: ByteArray): CapturedAsset? {
        // تجاهل التكرار
        val key = "$url|${bytes.size}"
        if (seen.contains(key)) return null
        seen.add(key)

        val type = classify(mime, url)
        val name = buildName(url, type)
        val file = File(dir, name)
        file.writeBytes(bytes)
        val asset = CapturedAsset(url, mime, file, bytes.size.toLong(), type)
        assets.add(asset)
        return asset
    }

    fun saveText(url: String, mime: String, text: String): CapturedAsset? =
        save(url, mime, text.toByteArray(Charsets.UTF_8))

    fun clear() {
        assets.clear()
        seen.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun classify(mime: String, url: String): String {
        val m = mime.lowercase()
        val u = url.lowercase().substringBefore('?')
        return when {
            m.contains("html") || u.matches(Regex(".*\\.html?$")) -> "html"
            m.contains("css") || u.endsWith(".css") -> "css"
            m.contains("javascript") || m.contains("ecmascript") ||
                    u.matches(Regex(".*\\.m?js$")) -> "js"
            m.startsWith("image/") ||
                    u.matches(Regex(".*\\.(png|jpe?g|gif|webp|svg|ico|bmp)$")) -> "img"
            m.contains("font") ||
                    u.matches(Regex(".*\\.(woff2?|ttf|otf|eot)$")) -> "font"
            m.contains("json") || u.endsWith(".json") -> "json"
            else -> "other"
        }
    }

    private fun buildName(url: String, type: String): String {
        val hash = url.hashCode().toUInt().toString(16).take(6)
        var base = url.substringAfterLast('/').substringBefore('?')
            .replace(Regex("[^\\w.\\-]"), "_")
            .take(50)
            .ifBlank { "index" }
        if (!base.contains('.')) {
            base += when (type) {
                "html" -> ".html"; "css" -> ".css"; "js" -> ".js"
                "json" -> ".json"; "img" -> ".png"; "font" -> ".woff2"
                else -> ".bin"
            }
        }
        return "$hash-$base"
    }

    fun zipAll(): File {
        val zip = File(ctx.cacheDir, "capture-${System.currentTimeMillis()}.zip")
        ZipOutputStream(BufferedOutputStream(zip.outputStream())).use { zos ->
            val folders = mapOf(
                "html" to "html", "css" to "css", "js" to "js",
                "img" to "images", "font" to "fonts", "json" to "json",
                "other" to "other"
            )
            assets.forEach { a ->
                val folder = folders[a.type] ?: "other"
                zos.putNextEntry(ZipEntry("$folder/${a.file.name}"))
                a.file.inputStream().copyTo(zos)
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("_manifest.txt"))
            val sb = StringBuilder()
            sb.append("عدد الملفات: ${assets.size}\n\n")
            assets.forEach { a ->
                sb.append("${folders[a.type]}/${a.file.name}\t${a.url}\t${a.size} bytes\n")
            }
            zos.write(sb.toString().toByteArray())
            zos.closeEntry()
        }
        return zip
    }
}
