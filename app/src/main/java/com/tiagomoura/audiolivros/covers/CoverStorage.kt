package com.tiagomoura.audiolivros.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/** Capas recortadas no centro em 3:4, até 900 × 1200 px, em `filesDir/covers/<bookId>.jpg`. */
@Singleton
class CoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun coverFile(bookId: String): File = File(context.filesDir, "covers/$bookId.jpg")

    /** Salva a capa a partir dos bytes de uma imagem. Retorna o caminho, ou nulo se não for imagem. */
    fun save(bookId: String, imageBytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_WIDTH && bounds.outHeight / (sampleSize * 2) >= MAX_HEIGHT) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        val upright = rotateByExif(decoded, imageBytes)

        val width = upright.width
        val height = upright.height
        val (cropWidth, cropHeight) = if (width * 4 > height * 3) {
            (height * 3 / 4) to height
        } else {
            width to (width * 4 / 3)
        }
        val cropped = Bitmap.createBitmap(upright, (width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
        val scale = min(1f, MAX_WIDTH.toFloat() / cropWidth)
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(cropped, (cropWidth * scale).roundToInt(), (cropHeight * scale).roundToInt(), true)
        } else {
            cropped
        }

        val file = coverFile(bookId)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file.absolutePath
    }

    /** Fotos da câmera vêm com a rotação só na etiqueta EXIF. */
    private fun rotateByExif(bitmap: Bitmap, imageBytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(imageBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun delete(bookId: String) {
        coverFile(bookId).delete()
    }

    private companion object {
        const val MAX_WIDTH = 900
        const val MAX_HEIGHT = 1200
        const val JPEG_QUALITY = 90
    }
}
