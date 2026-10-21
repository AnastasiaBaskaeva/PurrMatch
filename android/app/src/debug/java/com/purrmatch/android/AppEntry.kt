package com.purrmatch.android

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.purrmatch.presentation.App
import com.purrmatch.presentation.SampleApp
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.preview.ComponentsCatalog
import com.purrmatch.presentation.profile.Profile

@Composable
internal fun AppEntry(intent: Intent, photos: List<Painter>, onAddPhoto: () -> Unit, onContact: (String, Profile) -> Unit) {
    val sample = intent.getStringExtra("sample")
    val screen = intent.getStringExtra("screen")
    when {
        sample != null -> ComponentsCatalog(sample)
        screen != null -> SampleApp(Screen.fromRoute(screen), pickedPhotos = photos, onAddPhoto = onAddPhoto, onContact = onContact)
        else -> App(pickedPhotos = photos, onAddPhoto = onAddPhoto, onContact = onContact)
    }
}
