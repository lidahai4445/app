package com.paperly.mobile.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Android 原生 PdfRenderer 封装。PdfRenderer 非线程安全，所以用 Mutex 串行化。 */
class PdfDoc(file: File) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val pageCount: Int = renderer.pageCount

    /** 页面宽高比（高 / 宽）。 */
    suspend fun aspect(page: Int): Float = lock.withLock {
        withContext(Dispatchers.IO) { renderer.openPage(page).use { it.height.toFloat() / it.width } }
    }

    /** 以指定像素宽度渲染整页，白底。 */
    suspend fun render(page: Int, widthPx: Int): Bitmap {
        val w = widthPx.coerceIn(200, 2600)
        val k = "$page@$w"
        cache.get(k)?.let { return it }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                renderer.openPage(page).use { p ->
                    val h = (w * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }.also { cache.put(k, it) }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    companion object {
        fun pageCountOf(file: File): Int = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { f ->
            PdfRenderer(f).use { it.pageCount }
        }
    }
}
