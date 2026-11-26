package com.sparkstock.scanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Where the camera app saves a return photo, before it is shrunk and uploaded. */
fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "return-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
}

/** The photo upright, at most 1600 px on its long side, as JPEG: a few hundred KB instead of several MB. */
fun shrinkPhoto(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
    }
}
