package com.purrmatch.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.purrmatch.presentation.R

@Composable
fun Notice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    unavailable: Boolean = false,
    backgroundColor: Color = if (unavailable) Colors.Danger else Colors.Notice,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth()
            .dropShadow(shape, Shadow(20.dp, Colors.IconShadow, offset = DpOffset(0.dp, 8.dp)))
            .background(backgroundColor, shape)
            .border(1.dp, Color.White, shape).padding(18.dp),
    ) {
        if (title != null) Text(title, style = Typography.Field.copy(lineHeight = Typography.Body.lineHeight),
            color = Colors.NoticeText)
        Text(message, style = Typography.Body, color = Colors.NoticeText)
    }
}

@Composable
fun Mascot(modifier: Modifier = Modifier) {
    // Export of illustration 264:29 only, including its ears and two original shadow layers.
    Image(painterResource(R.drawable.mascot), null, modifier.size(177.dp))
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = {
        Image(painterResource(R.drawable.empty_search), null, Modifier.size(115.dp, 120.dp))
    },
    actionStyle: ButtonStyle = ButtonStyle.Primary,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        illustration()
        Text(title, Modifier.padding(top = 20.dp).semantics { heading() },
            style = Typography.Title, color = Colors.Ink, textAlign = TextAlign.Center)
        Text(message, Modifier.padding(top = 26.dp, bottom = 32.dp),
            style = Typography.Body, color = Colors.Body, textAlign = TextAlign.Center)
        Button(actionText, onAction, Modifier.fillMaxWidth(), actionStyle)
    }
}

enum class Confirmation { DeleteListing, Logout }

/** Dialog body is separate so it can also be previewed or placed inside a custom modal host. */
@Composable
fun ConfirmationCard(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon = Icon.Delete,
    dismissText: String = stringResource(R.string.cancel),
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().semantics { paneTitle = title }
            .dropShadow(shape, Shadow(24.dp, Colors.DialogShadow, offset = DpOffset(0.dp, 8.dp)))
            .background(Colors.Surface, shape).border(2.dp, Color.White, shape)
            .clip(shape).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Colors.Accent)
        Text(title, Modifier.semantics { heading() }, style = Typography.Title, color = Colors.Ink)
        Text(message, Modifier.padding(top = 2.dp), style = Typography.Body, color = Colors.Body)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically) {
            Button(dismissText, onDismiss, style = ButtonStyle.Text)
            Button(confirmText, onConfirm, style = ButtonStyle.Destructive)
        }
    }
}

@Composable
fun ConfirmationDialog(
    confirmation: Confirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConfirmationDialogContent(confirmation, onConfirm, onDismiss, modifier)
    }
}

/** Shared body for standalone dialogs and Navigation 3's dialog scene. */
@Composable
internal fun ConfirmationDialogContent(
    confirmation: Confirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deletion = confirmation == Confirmation.DeleteListing
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(Colors.Scrim.alpha) }
    ConfirmationCard(
        title = stringResource(if (deletion) R.string.delete_title else R.string.logout_title),
        message = stringResource(if (deletion) R.string.delete_message else R.string.logout_message),
        confirmText = stringResource(if (deletion) R.string.delete else R.string.logout),
        onConfirm = onConfirm, onDismiss = onDismiss,
        modifier = modifier.padding(horizontal = 24.dp),
        icon = if (deletion) Icon.Delete else Icon.Logout,
    )
}

@Preview(name = "Feedback", widthDp = 376)
@Composable
private fun FeedbackPreview() {
    Theme {
        Background {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Notice(stringResource(R.string.complete_profile_notice))
                Notice(stringResource(R.string.listing_deleted), title = stringResource(R.string.unavailable), unavailable = true)
                Mascot()
                EmptyState(
                    stringResource(R.string.browse_empty_title),
                    stringResource(R.string.expand_search),
                    stringResource(R.string.change_filters),
                    {},
                )
            }
        }
    }
}

@Preview(name = "Confirmations", widthDp = 376)
@Composable
private fun ConfirmationsPreview() {
    Theme {
        Background {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ConfirmationDialogContent(Confirmation.DeleteListing, {}, {})
                ConfirmationDialogContent(Confirmation.Logout, {}, {})
            }
        }
    }
}
