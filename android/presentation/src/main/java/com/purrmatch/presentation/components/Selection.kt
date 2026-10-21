package com.purrmatch.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 39.dp).alpha(if (enabled) 1f else .45f)
            .background(if (selected) Colors.ChipSelected else Colors.Chip, shape)
            .border(1.dp, if (selected) Colors.ChipSelectedBorder else androidx.compose.ui.graphics.Color.White, shape)
            .clip(shape).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Typography.Chip,
            color = if (selected) Colors.ChipSelectedText else Colors.ChipText)
    }
}

@Composable
fun ChipGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = Typography.GroupLabel, color = Colors.GroupLabel)
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ChipGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.ChipGap),
        ) {
            options.forEachIndexed { index, text ->
                FilterChip(text, index == selectedIndex, { onSelect(index) })
            }
        }
    }
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier.size(48.dp, 28.dp).alpha(if (enabled) 1f else .45f)
            .background(if (checked) Colors.Accent else Colors.RadioBorder, CircleShape)
            .clip(CircleShape).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape))
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(8.dp))
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = Typography.Body, color = Colors.Ink)
            // The whole row owns the toggle semantics and touch target.
            Box(Modifier.size(48.dp, 28.dp)
                .background(if (checked) Colors.Accent else Colors.RadioBorder, CircleShape)
                .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.size(22.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape)) }
        }
        if (showDivider) HorizontalDivider(color = Colors.Divider)
    }
}

@Composable
fun RadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.heightIn(min = 20.dp).alpha(if (enabled) 1f else .45f)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).border(2.dp, if (selected) Colors.Accent else Colors.RadioBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(Colors.Accent, CircleShape))
        }
        Text(text, style = Typography.Body, color = Colors.Ink)
    }
}
