package com.booknext.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun saveCoverFromUri(bookId: String, uri: Uri) {
        val coverFile = File(context.filesDir, "covers/$bookId.jpg")
        coverFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            coverFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun saveCoverBytes(bookId: String, bytes: ByteArray): String {
        val coverFile = File(context.filesDir, "covers/$bookId.jpg")
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(bytes)
        return coverFile.absolutePath
    }

    fun extractCoverFromFile(filePath: String, format: String): ByteArray? {
        return when (format.lowercase()) {
            "epub" -> extractEpubCover(filePath)
            "pdf" -> extractPdfCover(filePath)
            else -> null
        }
    }

    private fun extractEpubCover(epubPath: String): ByteArray? {
        return try {
            val zip = ZipFile(epubPath)
            val coverNames = listOf(
                "cover.jpg", "cover.png", "cover.jpeg",
                "OEBPS/cover.jpg", "OEBPS/cover.png",
                "OEBPS/images/cover.jpg", "OEBPS/Images/cover.jpg",
                "images/cover.jpg", "Images/cover.jpg",
                "META-INF/cover.jpg",
            )
            for (name in coverNames) {
                val entry = zip.getEntry(name) ?: continue
                return zip.getInputStream(entry).readBytes()
            }
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (!e.isDirectory && e.name.contains("cover", ignoreCase = true)
                    && (e.name.endsWith(".jpg") || e.name.endsWith(".jpeg") || e.name.endsWith(".png"))) {
                    return zip.getInputStream(e).readBytes()
                }
            }
            zip.close()
            null
        } catch (e: Exception) {
            android.util.Log.w("CoverService", "提取EPUB封面失败: ${e.message}")
            null
        }
    }

    private fun extractPdfCover(pdfPath: String): ByteArray? {
        return try {
            val file = File(pdfPath)
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount < 1) { renderer.close(); pfd.close(); return null }
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            android.util.Log.w("CoverService", "提取PDF封面失败: ${e.message}")
            null
        }
    }
}
