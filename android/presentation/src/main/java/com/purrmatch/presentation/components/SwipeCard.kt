package com.purrmatch.presentation.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
            Box(Modifier.matchParentSize().graphicsLayer {
                alpha = (abs(progress) / .33f).coerceAtMost(1f)
            }.background(Brush.horizontalGradient(*stops)))
        }
        if (progress != 0f) {
            val liking = progress > 0
            Icon(
                if (liking) Icon.SwipeLike else Icon.SwipeSkip,
                null,
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

/** Gestures and action buttons share the exit animation and async completion lock. */
@Composable
fun SwipeCard(
    onLike: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    cardKey: String = "",
    onSwipe: ((like: Boolean, complete: (Boolean) -> Unit) -> Unit)? = null,
    pendingLike: Boolean? = null,
    actions: (@Composable (enabled: Boolean, select: (Boolean) -> Unit) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // Keep the double-tap window across a fast repository result and card replacement.
    var buttonCooldown by remember { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    key(cardKey) {
        var progress by remember { mutableFloatStateOf(pendingLike?.let { if (it) 1.5f else -1.5f } ?: 0f) }
        var settling by remember { mutableStateOf(pendingLike != null) }
        var restoringPending by remember { mutableStateOf(pendingLike != null) }
        val scope = rememberCoroutineScope()
        val currentLike by rememberUpdatedState(onLike)
        val currentSkip by rememberUpdatedState(onSkip)
        val currentSwipe by rememberUpdatedState(onSwipe)
        val currentPendingLike by rememberUpdatedState(pendingLike)
        if (restoringPending) LaunchedEffect(pendingLike) {
            if (pendingLike == null) {
                animate(progress, 0f, animationSpec = tween(180)) { value, _ -> progress = value }
                settling = false
                restoringPending = false
            }
        }
        BoxWithConstraints(modifier) {
            val width = with(LocalDensity.current) { maxWidth.toPx() }
            fun settle(cancelled: Boolean = false, selection: Boolean? = null) {
                if (settling || currentPendingLike != null) return
                val commit = selection != null || (!cancelled && abs(progress) >= .25f)
                val direction = selection?.let { if (it) 1f else -1f } ?: sign(progress)
                settling = true
                scope.launch {
                    animate(progress, if (commit) direction * 1.5f else 0f, animationSpec = tween(180)) { value, _ ->
                        progress = value
                    }
                    if (!commit) {
                        settling = false
                        return@launch
                    }
                    val select = currentSwipe
                    if (select == null) {
                        if (direction > 0) currentLike() else currentSkip()
                        progress = 0f
                        settling = false
                    } else {
                        var completed = false
                        select(direction > 0) { advanced ->
                            if (!completed) {
                                completed = true
                                if (!advanced) scope.launch {
                                    animate(progress, 0f, animationSpec = tween(180)) { value, _ -> progress = value }
                                    settling = false
                                }
                            }
                        }
                        // Keep the completed card outside the viewport until cardKey changes.
                    }
                }
            }
            val card: @Composable () -> Unit = {
                SwipeCardFrame(
                    progress,
                    (if (actions == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).pointerInput(width) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settle() },
                            onDragCancel = { settle(cancelled = true) },
                            onHorizontalDrag = { change, amount ->
                                if (!settling && currentPendingLike == null && width > 0f) {
                                    change.consume()
                                    progress += amount / width
                                }
                            },
                        )
                    },
                    content,
                )
            }
            if (actions == null) card()
            else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { card() }
                actions(!settling && pendingLike == null && !buttonCooldown) { like ->
                    if (!buttonCooldown && !settling && currentPendingLike == null) {
                        buttonCooldown = true
                        actionScope.launch {
                            delay(doubleTapTimeout)
                            buttonCooldown = false
                        }
                        settle(selection = like)
                    }
                }
            }
        }
    }
}
