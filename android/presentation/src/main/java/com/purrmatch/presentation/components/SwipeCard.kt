package com.purrmatch.presentation.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Presentation of the swipe states 264:780 / 264:842; progress is in card-width units. */
@Composable
fun SwipeCardFrame(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier,
    ) {
        if (progress != 0f) {
            val liking = progress > 0
            val edge = if (liking) Colors.Like else Colors.Skip
            val fade = if (liking) Colors.LikeFade else Colors.SkipFade
            val stops = if (liking) arrayOf(0f to edge, .48f to fade.copy(alpha = .75f), 1f to fade.copy(alpha = 0f))
                else arrayOf(0f to fade.copy(alpha = 0f), .52f to fade.copy(alpha = .75f), 1f to edge)
            Box(Modifier.fillMaxWidth().height(535.dp).graphicsLayer {
                alpha = (abs(progress) / .33f).coerceAtMost(1f)
            }.background(Brush.horizontalGradient(*stops)))
        }
        if (progress != 0f) {
            val liking = progress > 0
            Icon(
                if (liking) Icon.SwipeLike else Icon.SwipeSkip,
                stringResource(if (liking) R.string.swipe_like else R.string.swipe_skip),
                Modifier.align(if (liking) Alignment.TopStart else Alignment.TopEnd)
                    .padding(horizontal = 23.dp, vertical = 127.dp).size(40.dp)
                    .background(Colors.SwipeIconSurface, androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                    .padding(9.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                translationX = progress * size.width
                rotationZ = progress * (7f / .33f)
            },
            content = content,
        )
    }
}

/** Short drags spring back. A completed drag emits exactly one selection callback. */
@Composable
fun SwipeCard(
    onLike: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentLike by rememberUpdatedState(onLike)
    val currentSkip by rememberUpdatedState(onSkip)
    BoxWithConstraints(modifier) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        fun settle(cancelled: Boolean = false) {
            if (settling) return
            val commit = !cancelled && abs(progress) >= .25f
            val direction = sign(progress)
            settling = true
            scope.launch {
                try {
                    animate(progress, if (commit) direction * 1.5f else 0f, animationSpec = tween(180)) { value, _ ->
                        progress = value
                    }
                    if (commit) {
                        if (direction > 0) currentLike() else currentSkip()
                    }
                } finally {
                    progress = 0f
                    settling = false
                }
            }
        }
        SwipeCardFrame(
            progress,
            Modifier.fillMaxSize().pointerInput(width) {
                detectHorizontalDragGestures(
                    onDragEnd = { settle() },
                    onDragCancel = { settle(cancelled = true) },
                    onHorizontalDrag = { change, amount ->
                        if (!settling && width > 0f) {
                            change.consume()
                            progress += amount / width
                        }
                    },
                )
            },
            content,
        )
    }
}
