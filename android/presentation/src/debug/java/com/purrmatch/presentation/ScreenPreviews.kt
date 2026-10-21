package com.purrmatch.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class ScreenPreviews : PreviewParameterProvider<Screen> {
    override val values = Screen.entries.asSequence()
}

@Preview(name = "Figma screens", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun ScreenPreview(@PreviewParameter(ScreenPreviews::class) screen: Screen) {
    SampleApp(initialScreen = screen)
}
