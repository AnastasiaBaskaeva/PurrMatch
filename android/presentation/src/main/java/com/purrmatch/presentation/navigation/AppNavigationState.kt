package com.purrmatch.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import com.purrmatch.presentation.components.Tab

/** All tab stacks stay decorated, including tabs that are currently off screen. */
internal class AppNavigationState(
    private val selected: MutableState<Tab>,
    val stacks: Map<Tab, NavBackStack<NavKey>>,
) {
    var tab by selected
    val stack get() = stacks.getValue(tab)
    val destination get() = stack.last()
    val showsTabs get() = destination == Home || destination == Favorites || destination == Profile

    fun open(destination: NavKey) {
        if (destination == this.destination) return
        stack.add(destination)
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else tab = Tab.Home
    }
    fun home() {
        tab = Tab.Home
        val home = stacks.getValue(Tab.Home)
        while (home.size > 1) home.removeAt(home.lastIndex)
    }
    fun profile() {
        tab = Tab.Profile
        val profile = stacks.getValue(Tab.Profile)
        while (profile.size > 1) profile.removeAt(profile.lastIndex)
    }

    @Composable
    fun entries(provider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = stacks.mapValues { (tab, stack) ->
            key(tab) {
                rememberDecoratedNavEntries(stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ), entryProvider = provider)
            }
        }
        return if (tab == Tab.Home) decorated.getValue(Tab.Home)
        else decorated.getValue(Tab.Home) + decorated.getValue(tab)
    }
}

@Composable
internal fun rememberAppNavigation(initial: NavKey = Home): AppNavigationState {
    val initialTab = when (initial) {
        Favorites -> Tab.Favorites
        Profile, Settings, CreateListing, is DeleteListing, Logout -> Tab.Profile
        else -> Tab.Home
    }
    val selected = rememberSaveable { mutableStateOf(initialTab) }
    val stacks = Tab.entries.associateWith { tab ->
        key(tab) {
            val root = when (tab) { Tab.Home -> Home; Tab.Favorites -> Favorites; Tab.Profile -> Profile }
            val keys = if (tab == initialTab && initial != root) listOf(root, initial) else listOf(root)
            rememberNavBackStack(*keys.toTypedArray())
        }
    }
    return remember { AppNavigationState(selected, stacks) }
}
