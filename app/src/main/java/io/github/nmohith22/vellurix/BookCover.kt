package io.github.nmohith22.vellurix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

@Composable
internal fun BookCover(item: CoverBookInfo, modifier: Modifier = Modifier, onDominantColor: ((Int) -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = produceState<Bitmap?>(null, item.uri, item.format) {
        value = withContext(Dispatchers.IO) { extractCover(context.applicationContext, item.uri, item.format) }
    }.value
    val tone = remember(bitmap) { bitmap?.let(::dominantColor) }
    SideEffect { tone?.let { onDominantColor?.invoke(it) } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Cover of ${item.title}", modifier = modifier, contentScale = ContentScale.Crop)
}

internal interface CoverBookInfo {
    val uri: String
    val format: String
    val title: String
}

private fun extractCover(context: Context, uriString: String, format: String): Bitmap? = runCatching {
    val uri = Uri.parse(uriString)
    val cache = File(context.cacheDir, "book-covers/${uriString.hashCode().toUInt().toString(16)}.png")
    if (cache.isFile) BitmapFactory.decodeFile(cache.path)?.let { return it }
    cache.parentFile?.mkdirs()
    val bitmap = when (format.uppercase()) {
        "PDF" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) null else renderer.openPage(0).use { page ->
                    val width = 420
                    val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }
        }
        "EPUB" -> epubCover(context, uri)
        else -> null
    } ?: return null
    cache.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    bitmap
}.getOrNull()

private fun epubCover(context: Context, uri: Uri): Bitmap? {
    val archive = File.createTempFile("vellurix-cover-", ".epub", context.cacheDir)
    return try {
        context.contentResolver.openInputStream(uri)?.use { input -> archive.outputStream().use { output -> input.copyTo(output) } } ?: return null
        ZipFile(archive).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return null
            val rootPath = zip.getInputStream(container).use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                var path: String? = null
                while (parser.next() != XmlPullParser.END_DOCUMENT && path == null) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") path = parser.getAttributeValue(null, "full-path")
                }
                path
            } ?: return null
            val opf = zip.getEntry(rootPath) ?: return null
            val coverHref = zip.getInputStream(opf).use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                var legacyCoverReference: String? = null
                val manifest = mutableMapOf<String, String>()
                var cover: String? = null
                while (parser.next() != XmlPullParser.END_DOCUMENT && cover == null) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name.substringAfter(':')) {
                        "meta" -> if (parser.getAttributeValue(null, "name") == "cover") legacyCoverReference = parser.getAttributeValue(null, "content")
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val properties = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id != null && href != null) manifest[id] = href
                            if (href != null && "cover-image" in properties.split(Regex("\\s+"))) cover = href
                        }
                    }
                }
                cover ?: legacyCoverReference?.let { manifest[it] ?: it }
            } ?: return null
            val rootFolder = File(rootPath).parent.orEmpty().replace('\\', '/')
            val decodedHref = Uri.decode(coverHref.substringBefore('#').substringBefore('?'))
            val imagePath = normalizeZipPath(if (rootFolder.isEmpty()) decodedHref else "$rootFolder/$decodedHref")
            val imageEntry = zip.getEntry(imagePath) ?: zip.getEntry(decodedHref) ?: return null
            zip.getInputStream(imageEntry).use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                val sample = maxOf(bounds.outWidth / 420, bounds.outHeight / 620, 1)
                zip.getInputStream(imageEntry).use { image ->
                    BitmapFactory.decodeStream(image, null, BitmapFactory.Options().apply { inSampleSize = sample.coerceAtMost(16) })
                }
            }
        }
    } finally {
        archive.delete()
    }
}

private fun normalizeZipPath(path: String): String {
    val segments = mutableListOf<String>()
    path.replace('\\', '/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> { if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex) }
            else -> segments.add(segment)
        }
    }
    return segments.joinToString("/")
}

private fun dominantColor(bitmap: Bitmap): Int {
    val buckets = HashMap<Int, Int>()
    val step = maxOf(bitmap.width, bitmap.height) / 28.coerceAtLeast(1)
    for (y in 0 until bitmap.height step step.coerceAtLeast(1)) {
        for (x in 0 until bitmap.width step step.coerceAtLeast(1)) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 220) continue
            val red = android.graphics.Color.red(pixel) shr 4
            val green = android.graphics.Color.green(pixel) shr 4
            val blue = android.graphics.Color.blue(pixel) shr 4
            val key = (red shl 8) or (green shl 4) or blue
            buckets[key] = (buckets[key] ?: 0) + 1
        }
    }
    val key = buckets.maxByOrNull { it.value }?.key ?: return android.graphics.Color.rgb(154, 91, 69)
    return android.graphics.Color.rgb(((key shr 8) and 15) * 17, ((key shr 4) and 15) * 17, (key and 15) * 17)
}
