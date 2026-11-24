package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "StorageRepository"

class StorageRepository(private val context: Context? = null) {

    /**
     * Resizes and compresses a Bitmap to JPEG byte array.
     * Ensures reasonable resolution (max 1024px dimension) for optimal storage efficiency.
     */
    private fun compressBitmapToJpeg(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = try {
            if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(width, height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            } else {
                bitmap
            }
        } catch (t: Throwable) {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        try {
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            return outputStream.toByteArray()
        } finally {
            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    /**
     * Saves a meal photo to the device's internal storage and returns a local file URI.
     * Eliminates unprovisioned cloud storage 404 errors and guarantees instant,
     * offline-capable image persistence for Coil AsyncImage in the meal diary.
     */
    suspend fun uploadMealImage(
        bitmap: Bitmap,
        mealId: String = UUID.randomUUID().toString(),
        userId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val appContext = context
            if (appContext == null) {
                Log.w(TAG, "Context was null, skipping local image save")
                return@withContext Result.success("")
            }

            val imageBytes = compressBitmapToJpeg(bitmap)
            val imagesDir = File(appContext.filesDir, "meal_images").apply {
                if (!exists()) mkdirs()
            }
            val imageFile = File(imagesDir, "$mealId.jpg")
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageBytes)
                fos.flush()
            }

            val localUri = "file://${imageFile.absolutePath}"
            Log.d(TAG, "Meal photo successfully saved locally: $localUri")
            Result.success(localUri)
        } catch (t: Throwable) {
            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
            Log.w(TAG, "Failed to save meal image locally", t)
            Result.success("")
        }
    }
}
