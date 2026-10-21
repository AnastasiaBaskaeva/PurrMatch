package com.purrmatch.android

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.net.toUri
import com.purrmatch.presentation.components.Background
import com.purrmatch.presentation.components.preview.ComponentsCatalog
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.profile.Profile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            var photoUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let { photoUris = ArrayList(photoUris + it.toString()) }
            }
            val photos by produceState<List<BitmapPainter>>(emptyList(), photoUris) {
                value = withContext(Dispatchers.IO) {
                    photoUris.mapNotNull { value ->
                        runCatching {
                            val uri = value.toUri()
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                            val options = BitmapFactory.Options().apply {
                                while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2
                            }
                            contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, options)?.let { BitmapPainter(it.asImageBitmap()) }
                            }
                        }.getOrNull()
                    }
                }
            }
            Background(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    val sample = intent.getStringExtra("sample")
                    if (sample != null) ComponentsCatalog(sample)
                    else App(
                        initialScreen = Screen.fromRoute(intent.getStringExtra("screen")), pickedPhotos = photos,
                        onAddPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onContact = ::openContact,
                    )
                }
            }
        }
    }

    private fun openContact(type: String, profile: Profile) {
        val address = when (type) {
            "website" -> profile.website.takeIf { it.isNotBlank() }?.let { if (it.startsWith("https://") || it.startsWith("http://")) it else "https://$it" }
            "telegram" -> profile.telegram.trim().removePrefix("@").takeIf { it.isNotBlank() }?.let { "https://t.me/$it" }
            "phone", "whatsapp" -> profile.phone.filter(Char::isDigit).takeIf { it.length >= 10 }?.let {
                if (type == "phone") "tel:$it" else "https://wa.me/$it"
            }
            else -> null
        }
        if (address == null) Toast.makeText(this, "Контакт не указан", Toast.LENGTH_SHORT).show()
        else runCatching { startActivity(Intent(if (type == "phone") Intent.ACTION_DIAL else Intent.ACTION_VIEW, address.toUri())) }
            .onFailure { Toast.makeText(this, "Не удалось открыть контакт", Toast.LENGTH_SHORT).show() }
    }
}
