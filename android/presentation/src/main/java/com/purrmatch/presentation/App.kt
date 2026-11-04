package com.purrmatch.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.purrmatch.presentation.navigation.PurrMatchApp
import com.purrmatch.presentation.profile.Profile
import dagger.hilt.android.EntryPointAccessors

/** Android entry point. Screen state belongs to entry-scoped ViewModels. */
@Composable
fun App(
    modifier: Modifier = Modifier,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, Profile) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val repositories = remember(context) {
        EntryPointAccessors.fromApplication(context, RepositoriesEntryPoint::class.java)
    }
    PurrMatchApp(repositories.sessions(), repositories.matches(), modifier, pickedPhotos, onAddPhoto, onContact)
}
