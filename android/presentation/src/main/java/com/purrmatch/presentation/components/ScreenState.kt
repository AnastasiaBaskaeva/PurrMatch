package com.purrmatch.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

@Composable
fun NavigationAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Preview(name = "ScreenStates", widthDp = 376)
@Composable
private fun ScreenStatesPreview() {
    Theme {
        Background {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                StateContent(ScreenState.Loading)
                StateContent(ScreenState.Empty)
                StateContent(ScreenState.Error(stringResource(R.string.listings_load_failed)))
                StateContent(ScreenState.Content) {
                    Text(stringResource(R.string.listings_loaded), style = Typography.Body)
                }
            }
        }
    }
}
