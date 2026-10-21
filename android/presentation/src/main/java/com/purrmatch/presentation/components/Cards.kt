package com.purrmatch.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R

enum class TagStyle { Lavender, Peach, Mint, Unavailable }

@Composable
fun ContactChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Tag(text, modifier.clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick))
    } else {
        Box(
            modifier.heightIn(min = 39.dp)
                .background(Colors.Chip, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = Typography.Chip, color = Colors.ChipText) }
    }
}

@Immutable
data class CatTag(val text: String, val style: TagStyle = TagStyle.Lavender)

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    style: TagStyle = TagStyle.Lavender,
) {
    val background = when (style) {
        TagStyle.Lavender -> Colors.Tag
        TagStyle.Peach -> Colors.Peach
        TagStyle.Mint -> Colors.Mint
        TagStyle.Unavailable -> Colors.Danger
    }
    val foreground = when (style) {
        TagStyle.Lavender -> Colors.TagText
        TagStyle.Peach -> Colors.PeachText
        TagStyle.Mint -> Colors.MintText
        TagStyle.Unavailable -> Colors.DangerText
    }
    Box(modifier.heightIn(min = 30.dp).background(background, RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Typography.Tag, color = foreground)
    }
}

@Composable
fun TagRow(tags: List<CatTag>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ChipGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.ChipGap),
    ) {
        tags.forEach { Tag(it.text, style = it.style) }
    }
}

/** Photo dimensions, inset, crop and city overlay match node 264:734. */
@Composable
fun CatCard(
    title: String,
    subtitle: String,
    photo: Painter,
    city: String?,
    tags: List<CatTag>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .dropShadow(shape, Shadow(30.dp, Colors.CardShadow, offset = DpOffset(0.dp, 16.dp)))
            .dropShadow(shape, Shadow(0.dp, Colors.CardStep, offset = DpOffset(0.dp, 8.dp)))
            .background(Colors.Surface, shape).border(2.dp, Color.White, shape)
            .clip(shape).clickable(role = Role.Button, onClick = onClick).padding(2.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(332f / 365f)
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))) {
            Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (city != null) Tag(
                city, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 18.5.dp).heightIn(min = 40.dp),
                TagStyle.Mint,
            )
        }
        Column(Modifier.heightIn(min = 139.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.heightIn(min = 27.dp), style = Typography.Title, color = Colors.Ink)
            Text(subtitle, Modifier.heightIn(min = 22.dp), style = Typography.Body, color = Colors.Body)
            TagRow(tags, Modifier.padding(top = 4.dp))
        }
    }
}

/** A blank image slot is intentional when photo is null, matching the Figma list cards. */
@Composable
fun CatListItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photo: Painter? = null,
    available: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(21.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 106.dp)
            .dropShadow(shape, Shadow(0.dp, Colors.ListStep, offset = DpOffset(0.dp, 4.dp)))
            .background(Colors.Surface, shape).border(1.dp, Color.White, shape)
            .clip(shape).clickable(role = Role.Button, onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(17.dp))) {
            if (photo != null) Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = Typography.Heading, color = Colors.Ink)
            if (available) Text(subtitle, style = Typography.Caption, color = Colors.Body)
            else Tag(stringResource(R.string.unavailable), style = TagStyle.Unavailable)
        }
        trailingContent?.invoke(this)
    }
}

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    photo: Painter? = null,
    contentDescription: String? = null,
) {
    Box(
        modifier.size(64.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 27.dp, bottomEnd = 27.dp))
            .background(Colors.Avatar),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) Image(photo, contentDescription, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Icon(Icon.Profile, contentDescription, Modifier.size(34.dp), tint = Colors.Accent)
    }
}

@Composable
fun ProfileHeader(
    name: String,
    location: String,
    modifier: Modifier = Modifier,
    photo: Painter? = null,
) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(photo = photo)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = Typography.Title, color = Colors.Ink)
            Text(location, style = Typography.Caption, color = Colors.Body)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = Typography.Body, color = Colors.Ink)
        Text(value, style = Typography.Field, color = Colors.Ink)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = Typography.Heading, color = Colors.Ink)
        trailingContent?.invoke()
    }
}
