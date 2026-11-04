package com.purrmatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.session.MockSessionRepository
import com.purrmatch.presentation.browse.HomeScreen
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.navigation.*
import com.purrmatch.presentation.profile.Profile as ProfileData
import kotlinx.coroutines.runBlocking

/** Figma samples and direct routes exist only in debug builds. */
enum class Screen(val route: String) {
    Login("login"), Registration("registration"), CompleteProfile("complete-profile"),
    Home("home"), Filters("filters"), NoResults("no-results"), Listing("listing"),
    Favorites("favorites"), Unavailable("unavailable"), EmptyFavorites("empty-favorites"),
    Profile("profile"), NewListing("new-listing"), Delete("delete"), Settings("settings"),
    Author("author"), Logout("logout"), SwipeRight("swipe-right"), SwipeLeft("swipe-left");
    companion object {
        fun fromRoute(route: String?) = entries.firstOrNull { it.route == route } ?: Login
    }
}

private class SampleDependencies(screen: Screen) : ViewModel() {
    private val backend = MockBackend(
        fixtureName = when (screen) {
            Screen.CompleteProfile -> "complete-profile"
            Screen.NoResults -> "no-results"
            Screen.EmptyFavorites -> "empty-favorites"
            else -> "catalog"
        },
        latencyMillis = 0,
    )
    val matches = MockMatchRepository(backend)
    val sessions = MockSessionRepository(backend)

    init {
        // Debug-only setup is deterministic, with no simulated network delay.
        runBlocking {
            if (screen != Screen.Login && screen != Screen.Registration) {
                // The deck leaves out the reader's own listings, and in the catalog they all belong to Anna.
                val user = if (screen in listOf(Screen.Home, Screen.Filters)) "olga" else "anna"
                matches.load(user).getOrThrow()
                // The swipe previews draw this listing without going through the feed.
                if (screen == Screen.SwipeRight || screen == Screen.SwipeLeft) matches.getListing("mia").getOrThrow()
                sessions.signIn(user).getOrThrow()
            }
        }
    }
}

@Composable
fun SampleApp(
    initialScreen: Screen,
    modifier: Modifier = Modifier,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, ProfileData) -> Unit = { _, _ -> },
) {
    val dependencies = viewModel<SampleDependencies>(
        key = "sample-${initialScreen.route}",
        factory = viewModelFactory { initializer { SampleDependencies(initialScreen) } },
    )
    val data by dependencies.matches.data.collectAsStateWithLifecycle()
    if (initialScreen == Screen.SwipeRight || initialScreen == Screen.SwipeLeft) {
        Theme { Background(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                HomeScreen(data.listings.first(), designCatPhoto(), {}, {}, {}, {},
                    Modifier.weight(1f), if (initialScreen == Screen.SwipeRight) .33f else -.33f)
                BottomBar(Tab.Home, {}, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
            }
        } }
    } else PurrMatchApp(dependencies.sessions, dependencies.matches, modifier, pickedPhotos, onAddPhoto, onContact,
        initialDestination = when (initialScreen) {
            Screen.Login -> Login
            Screen.Registration -> Registration
            Screen.CompleteProfile -> CompleteProfile
            Screen.Filters -> Filters
            Screen.Listing -> ListingDetails("mia")
            Screen.Unavailable -> ListingDetails("sonya")
            Screen.Favorites, Screen.EmptyFavorites -> Favorites
            Screen.Profile -> Profile
            Screen.NewListing -> CreateListing
            Screen.Delete -> DeleteListing("mia")
            Screen.Settings -> Settings
            Screen.Author -> AuthorProfile("anna")
            Screen.Logout -> Logout
            else -> Home
        })
}
