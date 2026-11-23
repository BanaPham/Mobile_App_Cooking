package com.cooking.app.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal suspend fun photoPart(resolver: ContentResolver, uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, size) }
    if (size.outWidth <= 0 || size.outHeight <= 0) throw IllegalArgumentException("Không đọc được ảnh")
    var sample = 1
    while (maxOf(size.outWidth, size.outHeight) / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Không đọc được ảnh")
    try {
        val buf = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, buf)) throw IllegalArgumentException("Không nén được ảnh")
        MultipartBody.Part.createFormData("file", "photo.jpg", buf.toByteArray().toRequestBody("image/jpeg".toMediaType()))
    } finally {
        bitmap.recycle()
    }
}
