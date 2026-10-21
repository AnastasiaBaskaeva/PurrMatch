package com.purrmatch.android

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.purrmatch.presentation.App
import com.purrmatch.presentation.profile.Profile

@Composable
internal fun AppEntry(intent: Intent, photos: List<Painter>, onAddPhoto: () -> Unit, onContact: (String, Profile) -> Unit) {
    App(pickedPhotos = photos, onAddPhoto = onAddPhoto, onContact = onContact)
}
