package com.greenhands.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a Photo Picker URI into private app storage and resizes it so the
 * avatar survives process death without broad storage permission.
 */
class LocalProfilePhotoStore(
    context: Context,
    private val maxEdgePx: Int = 512,
    private val jpegQuality: Int = 85
) {
    private val appContext = context.applicationContext
    private val directory: File = File(appContext.filesDir, "profile").apply {
        if (!exists()) mkdirs()
    }

    fun importPickerUri(uri: Uri): String? {
        return try {
            val bitmap = decodeSampled(uri, maxEdgePx) ?: return null
            val scaled = scaleToMaxEdge(bitmap, maxEdgePx)
            if (scaled != bitmap) {
                bitmap.recycle()
            }
            val file = File(directory, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }
            scaled.recycle()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun decodeSampled(uri: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
        }
        return appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxEdge) return source
        val scale = maxEdge.toFloat() / largest.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var inSampleSize = 1
        val largest = maxOf(width, height)
        if (largest > maxSize) {
            var half = largest / 2
            while (half / inSampleSize >= maxSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
