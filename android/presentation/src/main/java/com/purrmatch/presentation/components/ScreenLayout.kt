package com.purrmatch.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R

/** Screen content starts below the real system status bar. Insets belong to the activity. */
@Composable
internal fun ScreenLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: Icon = Icon.Back,
    actions: @Composable RowScope.() -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopBar(title, navigationIcon = onBack?.let { back ->
            { IconButton(navigationIcon, "Назад", back) }
        }, actions = actions)
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        footer()
    }
}

@Composable
internal fun FormContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 24.dp), content = content)
}

@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = Typography.Heading, color = Colors.Ink)
}

@Composable
internal fun Body(text: String, modifier: Modifier = Modifier, caption: Boolean = false) {
    Text(text, modifier, style = if (caption) Typography.Caption else Typography.Body,
        color = Colors.Body)
}

@Composable
internal fun Divider() = HorizontalDivider(color = Colors.Divider)

/** The original image transform is shared by the card and gallery in Figma. */
@Composable
internal fun designCatPhoto(detail: Boolean = false): Painter {
    val original = painterResource(R.drawable.sample_cat)
    return remember(original, detail) {
        object : Painter() {
            override val intrinsicSize = Size(if (detail) 336f else 332f, if (detail) 297f else 365f)
            override fun DrawScope.onDraw() {
                val scale = size.width / intrinsicSize.width
                clipRect {
                    translate(-57f * scale, -61.01f * scale) {
                        with(original) { draw(Size(419f * scale, 476f * scale)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun designUploadPhoto(): Painter {
    val original = painterResource(R.drawable.sample_upload)
    return remember(original) {
        object : Painter() {
            override val intrinsicSize = Size(102f, 102f)
            override fun DrawScope.onDraw() {
                val scale = size.width / 102f
                clipRect {
                    translate(-34.5f * scale, -13f * scale) {
                        with(original) { draw(Size(171f * scale, 128f * scale)) }
                    }
                }
            }
        }
    }
}
