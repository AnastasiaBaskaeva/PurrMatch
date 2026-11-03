package com.purrmatch.presentation.photos

import android.content.ContentResolver
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

private const val MAX_PHOTO_SIZE = 1600

@Composable
fun rememberPickedPhotos(uris: List<String>): List<Painter> {
    val resolver = LocalContext.current.contentResolver
    val photos by produceState<List<Painter>>(emptyList(), uris, resolver) {
        value = uris.mapNotNull { loadPickedPhoto(resolver, it) }
    }
    return photos
}

private suspend fun loadPickedPhoto(
    resolver: ContentResolver,
    reference: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): BitmapPainter? = withContext(dispatcher) {
    ensureActive()
    try {
        val uri = reference.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > MAX_PHOTO_SIZE || bounds.outHeight / inSampleSize > MAX_PHOTO_SIZE) {
                inSampleSize *= 2
            }
        }
        ensureActive()
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.let { BitmapPainter(it.asImageBitmap()) }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
