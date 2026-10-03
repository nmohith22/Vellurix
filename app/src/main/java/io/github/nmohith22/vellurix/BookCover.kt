package io.github.nmohith22.vellurix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
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
internal fun BookCover(item: CoverBookInfo, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = produceState<Bitmap?>(null, item.uri, item.format) {
        value = withContext(Dispatchers.IO) { extractCover(context.applicationContext, item.uri, item.format) }
    }.value
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
                var legacyCoverId: String? = null
                val manifest = mutableMapOf<String, String>()
                var cover: String? = null
                while (parser.next() != XmlPullParser.END_DOCUMENT && cover == null) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name.substringAfter(':')) {
                        "meta" -> if (parser.getAttributeValue(null, "name") == "cover") legacyCoverId = parser.getAttributeValue(null, "content")
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val properties = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id != null && href != null) manifest[id] = href
                            if (href != null && "cover-image" in properties.split(Regex("\\s+"))) cover = href
                        }
                    }
                }
                cover ?: legacyCoverId?.let(manifest::get)
            } ?: return null
            val imagePath = File(File(rootPath).parent ?: "", coverHref).path.replace('\\', '/')
            val imageEntry = zip.getEntry(imagePath) ?: zip.getEntry(coverHref) ?: return null
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
