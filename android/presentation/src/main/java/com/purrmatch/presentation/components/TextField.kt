package com.purrmatch.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R
import java.util.Locale

private val FieldShape = RoundedCornerShape(17.dp)

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    multiline: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = Colors.Muted,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val labelColor = when {
        errorMessage != null -> Colors.DestructiveText
        focused -> Colors.Accent
        else -> Colors.FieldLabel
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = if (multiline) 100.dp else 62.dp)
                .alpha(if (enabled) 1f else .45f)
                .dropShadow(FieldShape, Shadow(18.dp, Colors.FieldShadow, offset = DpOffset(0.dp, 5.dp)))
                .background(if (readOnly) Colors.ReadOnly else Colors.Field, FieldShape)
                .border(1.dp, if (errorMessage != null || focused) labelColor else Colors.FieldBorder, FieldShape)
                .semantics(mergeDescendants = true) { if (errorMessage != null) error(errorMessage) },
        ) {
            Text(
                label.uppercase(Locale.ROOT), Modifier.padding(start = 17.dp, top = 9.dp),
                style = Typography.FieldLabel, color = labelColor,
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 17.dp, end = 17.dp, top = if (multiline) 32.dp else 26.dp, bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = Typography.Field.copy(color = textColor),
                    singleLine = !multiline,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Colors.Accent),
                    interactionSource = interaction,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) Text(placeholder, style = Typography.Field, color = Colors.Muted)
                            innerTextField()
                        }
                    },
                )
                trailingContent?.invoke()
            }
        }
        if (errorMessage != null) Text(errorMessage, style = Typography.Caption, color = Colors.DestructiveText)
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value, onValueChange, label, modifier, placeholder = placeholder, enabled = enabled,
        errorMessage = errorMessage,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation('•'),
        trailingContent = {
            val description = stringResource(if (visible) R.string.hide_password else R.string.show_password)
            Box(Modifier.size(21.dp).clickable(enabled = enabled, role = Role.Button) { onVisibilityChange(!visible) }) {
                Icon(Icon.PasswordHidden, description, Modifier.size(21.dp))
            }
        },
    )
}

@Composable
fun SelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon = Icon.ChevronDown,
    enabled: Boolean = true,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 62.dp).alpha(if (enabled) 1f else .45f)
            .dropShadow(FieldShape, Shadow(18.dp, Colors.FieldShadow, offset = DpOffset(0.dp, 5.dp)))
            .background(Colors.Field, FieldShape).border(1.dp, Colors.FieldBorder, FieldShape)
            .clip(FieldShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(label.uppercase(Locale.ROOT), Modifier.padding(start = 17.dp, top = 9.dp),
            style = Typography.FieldLabel, color = Colors.FieldLabel)
        Row(
            Modifier.fillMaxWidth().padding(start = 17.dp, end = 17.dp, top = 26.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f), style = Typography.Field, color = Colors.Muted)
            Icon(icon, null, Modifier.size(21.dp))
        }
    }
}

@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SelectField(label, value, { expanded = true }, enabled = enabled)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option, style = Typography.Field) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = Colors.ReadOnly,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp).background(backgroundColor, FieldShape)
            .semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label.uppercase(Locale.ROOT), style = Typography.ReadOnlyLabel, color = Colors.ReadOnlyLabel)
            Text(value, style = Typography.Field, color = Colors.ReadOnlyText)
        }
        Icon(Icon.Lock, null, Modifier.size(19.dp))
    }
}
