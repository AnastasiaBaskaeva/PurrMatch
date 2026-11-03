package com.purrmatch.android

import android.content.Intent
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
import androidx.core.net.toUri
import com.purrmatch.presentation.R
import com.purrmatch.presentation.components.Background
import com.purrmatch.presentation.contacts.contactAddress
import com.purrmatch.presentation.photos.rememberPickedPhotos
import com.purrmatch.presentation.profile.Profile
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
        )
        setContent {
            var photoUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let { photoUris = ArrayList(photoUris + it.toString()) }
            }
            val photos = rememberPickedPhotos(photoUris)
            Background(Modifier.fillMaxSize()) {
                Box(Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()) {
                    AppEntry(
                        intent,
                        photos,
                        onAddPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onContact = ::openContact,
                    )
                }
            }
        }
    }

    private fun openContact(
        type: String,
        profile: Profile
    ) {
        val address = profile.contactAddress(type)
        if (address == null) Toast.makeText(
            this,
            R.string.contact_missing,
            Toast.LENGTH_SHORT
        ).show()
        else runCatching {
            startActivity(
                Intent(
                    if (type == "phone") Intent.ACTION_DIAL else Intent.ACTION_VIEW,
                    address.toUri()
                )
            )
        }
            .onFailure {
                Toast.makeText(
                    this,
                    R.string.contact_open_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
