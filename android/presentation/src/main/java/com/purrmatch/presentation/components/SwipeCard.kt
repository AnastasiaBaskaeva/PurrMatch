package com.purrmatch.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawWithCache
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
    SwipeCardFrame({ progress }, modifier, content)
}

@Composable
private fun SwipeCardFrame(
    progress: () -> Float,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentProgress by rememberUpdatedState(progress)
    val direction by remember { derivedStateOf { sign(currentProgress()) } }
    Box(modifier) {
        if (direction != 0f) Box(Modifier.matchParentSize().drawWithCache {
            val like = Brush.horizontalGradient(0f to Colors.Like,
                .48f to Colors.LikeFade.copy(alpha = .75f), 1f to Colors.LikeFade.copy(alpha = 0f))
            val skip = Brush.horizontalGradient(0f to Colors.SkipFade.copy(alpha = 0f),
                .52f to Colors.SkipFade.copy(alpha = .75f), 1f to Colors.Skip)
            onDrawBehind {
                val value = currentProgress()
                drawRect(if (value > 0) like else skip, alpha = (abs(value) / .33f).coerceAtMost(1f))
            }
        })
        if (direction != 0f) {
            val liking = direction > 0
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
                val value = currentProgress()
                translationX = value * size.width
                rotationZ = value * (7f / .33f)
            },
            content = content,
        )
    }
}

/**
 * Gestures and action buttons share exit, entrance and async completion locks.
 * [cardKey] identifies the displayed listing; [onSwipe] reports whether the deck advanced.
 * Failed or unchanged selections spring back. [pendingLike] restores an in-flight selection.
 */
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
        var settling by remember { mutableStateOf(true) }
        var restoringPending by remember { mutableStateOf(pendingLike != null) }
        val entrance = remember { Animatable(if (pendingLike == null) 0f else 1f) }
        val scope = rememberCoroutineScope()
        val currentLike by rememberUpdatedState(onLike)
        val currentSkip by rememberUpdatedState(onSkip)
        val currentSwipe by rememberUpdatedState(onSwipe)
        val currentPendingLike by rememberUpdatedState(pendingLike)
        LaunchedEffect(Unit) {
            if (!restoringPending) {
                entrance.animateTo(1f, tween(220))
                settling = false
            }
        }
        if (restoringPending) LaunchedEffect(pendingLike) {
            if (pendingLike == null) {
                animate(progress, 0f, animationSpec = spring()) { value, _ -> progress = value }
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
                    if (!commit) {
                        animate(progress, 0f, animationSpec = spring()) { value, _ -> progress = value }
                        settling = false
                        return@launch
                    }
                    animate(progress, direction * 1.5f, animationSpec = tween(180)) { value, _ -> progress = value }
                    val select = currentSwipe
                    if (select == null) {
                        if (direction > 0) currentLike() else currentSkip()
                    } else {
                        var completed = false
                        select(direction > 0) { advanced ->
                            if (!completed) {
                                completed = true
                                if (!advanced) scope.launch {
                                    animate(progress, 0f, animationSpec = spring()) { value, _ -> progress = value }
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
                    { progress },
                    (if (actions == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).graphicsLayer {
                        alpha = entrance.value
                        scaleX = .94f + .06f * entrance.value
                        scaleY = scaleX
                    }.pointerInput(width) {
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
