package com.purrmatch.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

internal const val TAB_ROOT = "purrmatch.tabRoot"
internal const val CONFIRMATION = "purrmatch.confirmation"

/** Keep tab changes and confirmations on NavDisplay's crossfade; slide through screen history. */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.navigationTransition(isPop: Boolean): ContentTransform {
    if (initialState.tabRoot() != targetState.tabRoot() ||
        initialState.metadata[CONFIRMATION] == true || targetState.metadata[CONFIRMATION] == true
    ) {
        return if (isPop) defaultPopTransitionSpec<NavKey>().invoke(this)
        else defaultTransitionSpec<NavKey>().invoke(this)
    }
    val direction = if (isPop) AnimatedContentTransitionScope.SlideDirection.End
    else AnimatedContentTransitionScope.SlideDirection.Start
    return slideIntoContainer(direction, animationSpec = tween(300)) togetherWith
        slideOutOfContainer(direction, animationSpec = tween(300))
}

private fun Scene<NavKey>.tabRoot(): Any? =
    (previousEntries + entries).lastOrNull { TAB_ROOT in it.metadata }?.metadata?.get(TAB_ROOT)
