package com.purrmatch.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.purrmatch.presentation.navigation.PurrMatchApp
import com.purrmatch.presentation.profile.Profile
import com.purrmatch.presentation.session.SessionViewModel

/** Android entry point. Screen state belongs to entry-scoped ViewModels. */
@Composable
fun App(
    modifier: Modifier = Modifier,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, Profile) -> Unit = { _, _ -> },
) {
    val session: SessionViewModel = hiltViewModel()
    PurrMatchApp(session.sessions, session.matches, modifier, pickedPhotos, onAddPhoto, onContact)
}
