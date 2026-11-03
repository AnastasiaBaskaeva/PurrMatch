package com.purrmatch.presentation.components

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

enum class ButtonStyle { Primary, Warm, Secondary, Outlined, Text, Destructive }

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: Icon? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val primary = style == ButtonStyle.Primary || style == ButtonStyle.Warm
    val shape = RoundedCornerShape(when {
        primary -> 20.dp
        style == ButtonStyle.Destructive -> 13.dp
        else -> 17.dp
    })
    val color = when (style) {
        ButtonStyle.Primary, ButtonStyle.Warm -> Colors.PrimaryText
        ButtonStyle.Secondary -> Colors.SecondaryText
        ButtonStyle.Outlined -> Colors.OutlineText
        ButtonStyle.Text -> Colors.Link
        ButtonStyle.Destructive -> Colors.DestructiveText
    }
    val decoration = when (style) {
        ButtonStyle.Primary, ButtonStyle.Warm -> Modifier
            .dropShadow(shape, Shadow(22.dp, Colors.ButtonShadow, offset = DpOffset(0.dp, 12.dp)))
            .dropShadow(shape, Shadow(0.dp, Colors.PrimaryStep, offset = DpOffset(0.dp, 5.dp)))
            .drawWithCache {
                val brush = linearBrush(size, 118.27068f,
                    0f to if (style == ButtonStyle.Warm) Colors.PublishStart else Colors.PrimaryStart,
                    1f to if (style == ButtonStyle.Warm) Colors.PublishEnd else Colors.PrimaryEnd,
                )
                onDrawBehind { drawRoundRect(brush, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())) }
            }
            .border(1.dp, Colors.PrimaryBorder, shape)
        ButtonStyle.Secondary -> Modifier
            .dropShadow(shape, Shadow(0.dp, Colors.CardStep, offset = DpOffset(0.dp, 3.dp)))
            .background(Colors.Secondary, shape).border(1.dp, Colors.SecondaryBorder, shape)
        ButtonStyle.Outlined -> Modifier.background(Colors.OutlineSurface, shape)
            .border(1.dp, Colors.Outline, shape)
        ButtonStyle.Text -> Modifier
        ButtonStyle.Destructive -> Modifier.background(Colors.Destructive, shape)
    }
    Row(
        modifier.alpha(if (enabled) 1f else .45f).then(decoration)
            .clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (style == ButtonStyle.Destructive) 42.dp else if (primary) 54.dp else 52.dp)
            .padding(horizontal = when {
                style == ButtonStyle.Text -> 10.dp
                style == ButtonStyle.Destructive -> 16.dp
                else -> 22.dp
            }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(24.dp))
        Text(text, style = Typography.Button, color = color)
        trailingContent?.invoke(this)
    }
}

@Composable
fun IconButton(
    icon: Icon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier.defaultMinSize(40.dp, 40.dp).alpha(if (enabled) 1f else .45f)
            .dropShadow(CircleShape, Shadow(12.dp, Colors.IconShadow, offset = DpOffset(0.dp, 3.dp)))
            .background(Colors.IconSurface, CircleShape).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(23.dp))
    }
}

@Preview(name = "Buttons", widthDp = 376)
@Composable
private fun ButtonsPreview() {
    Theme {
        Background {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ButtonStyle.entries.forEach { style ->
                    Button(style.name, {}, Modifier.fillMaxWidth(), style = style)
                }
                Button(stringResource(R.string.disabled_button), {}, Modifier.fillMaxWidth(), enabled = false)
                IconButton(Icon.Settings, stringResource(R.string.settings), {})
            }
        }
    }
}
