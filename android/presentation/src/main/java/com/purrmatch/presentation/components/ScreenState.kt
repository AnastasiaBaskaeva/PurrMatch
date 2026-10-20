package com.purrmatch.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.purrmatch.presentation.R

/** Screen ViewModels can expose this alongside their feature-specific data. */
sealed interface ScreenState {
    data object Loading : ScreenState
    data object Empty : ScreenState
    data class Error(val message: String) : ScreenState
    data object Content : ScreenState
}

@Composable
fun StateContent(
    state: ScreenState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Box(modifier) {
        when (state) {
            ScreenState.Loading -> CircularProgressIndicator()
            ScreenState.Empty -> Text(stringResource(R.string.empty_state))
            is ScreenState.Error -> Column {
                Text(state.message)
                NavigationAction(stringResource(R.string.retry), onClick = onRetry)
            }
            ScreenState.Content -> content()
        }
    }
}
