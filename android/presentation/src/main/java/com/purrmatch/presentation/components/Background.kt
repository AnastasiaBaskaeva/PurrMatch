package com.purrmatch.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun linearBrush(size: Size, angle: Float, vararg stops: Pair<Float, Color>): Brush {
    val radians = Math.toRadians(angle.toDouble())
    val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    val length = abs(size.width * direction.x) + abs(size.height * direction.y)
    val center = Offset(size.width / 2, size.height / 2)
    return Brush.linearGradient(
        colorStops = stops,
        start = center - direction * (length / 2),
        end = center + direction * (length / 2),
    )
}

/** Three editable gradient layers from the design; contains no screen-sized bitmap. */
@Composable
fun Background(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.drawWithCache {
            val base = linearBrush(size, 150.48954f,
                0f to Colors.Lavender,
                .6f to Colors.Cream,
                1f to Colors.Lilac,
            )
            val mintCenter = Offset(0f, size.height * .24f)
            val pinkCenter = Offset(size.width, size.height * .63f)
            val mint = Brush.radialGradient(
                0f to Colors.MintGlow,
                .35f to Colors.MintGlow.copy(alpha = 0f),
                center = mintCenter, radius = size.width * 1.4141f,
            )
            val pink = Brush.radialGradient(
                0f to Colors.PinkGlow,
                .38f to Colors.PinkGlow.copy(alpha = 0f),
                center = pinkCenter, radius = size.width * 1.4141f,
            )
            onDrawBehind {
                drawRect(base)
                withTransform({ scale(1f, 1.36336f, pinkCenter) }) {
                    drawRect(pink, topLeft = Offset(0f, -size.height), size = Size(size.width, size.height * 3))
                }
                withTransform({ scale(1f, 1.64472f, mintCenter) }) {
                    drawRect(mint, topLeft = Offset(0f, -size.height), size = Size(size.width, size.height * 3))
                }
            }
        },
        content = content,
    )
}

/** Fading footer used by forms and cat details. System insets belong to the host screen. */
@Composable
fun ActionFooter(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 11.dp, end = 20.dp, bottom = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(
                0f to Colors.Lavender.copy(alpha = 0f),
                .38f to Colors.Lavender.copy(alpha = .94f),
            ))
            .padding(contentPadding),
        content = content,
    )
}
