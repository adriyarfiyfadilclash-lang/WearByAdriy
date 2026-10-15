package com.adriy.wearbyadriy.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val MAX_SIDE = 1600
    const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_INPUT_BYTES = 30L * 1024 * 1024

    /** Validates an image from the picker or camera and returns it as a resized JPEG. */
    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)
        if (type != null && !type.startsWith("image/")) throw UserError("File yang dipilih bukan gambar.")

        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > MAX_INPUT_BYTES) throw UserError("Foto terlalu besar (maksimal 30 MB sebelum dikompres).")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw UserError("Foto tidak bisa dibuka.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UserError("File ini tidak bisa dibaca sebagai gambar.")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw UserError("Foto tidak bisa dibuka.")

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
        val bitmap = if (scale < 1f || rotation != 0f)
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded

        var quality = 86
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
            quality -= 12
        } while (bytes.size > MAX_BYTES && quality > 40)
        if (bitmap !== decoded) decoded.recycle()
        bitmap.recycle()
        if (bytes.size > MAX_BYTES) throw UserError("Foto masih terlalu besar setelah dikompres.")
        bytes
    }
}
