package com.purrmatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.match.sampleMatchData
import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.UserSelection
import com.purrmatch.domain.session.SessionState
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

private class SampleDependencies(val matches: MockMatchRepository, val sessions: LocalSessionRepository)
private val sampleSaver = listSaver<SampleDependencies, Any>(
    save = {
        val session = it.sessions.session.value as? SessionState.SignedIn
        listOf(it.matches.data.value, session?.userId.orEmpty(), session?.onboarding ?: false)
    },
    restore = { values -> SampleDependencies(MockMatchRepository(initialData = values[0] as MatchData),
        LocalSessionRepository().also { sessions ->
            if ((values[1] as String).isNotEmpty()) runBlocking { sessions.signIn(values[1] as String, values[2] as Boolean) }
        }) },
)

@Composable
fun SampleApp(
    initialScreen: Screen,
    modifier: Modifier = Modifier,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, ProfileData) -> Unit = { _, _ -> },
) {
    val dependencies = rememberSaveable(initialScreen, saver = sampleSaver) {
        var data = sampleMatchData().let { it.copy(listings = it.listings.filterNot { listing -> listing.id == "barsik" }) }
        if (initialScreen == Screen.CompleteProfile) data = data.copy(profiles = data.profiles.map {
            if (it.id == "anna") it.copy(city = "", about = "", telegram = "", phone = "", website = "") else it
        })
        if (initialScreen == Screen.NoResults) data = data.copy(listings = emptyList())
        if (initialScreen == Screen.EmptyFavorites) data = data.copy(selections = mapOf("anna" to UserSelection()))
        if (initialScreen in listOf(Screen.Settings, Screen.Logout)) data = data.copy(profiles = data.profiles.map {
            if (it.id == "anna") it.copy(about = "Помогаю кошкам находить дом") else it
        })
        SampleDependencies(MockMatchRepository(initialData = data), LocalSessionRepository().also { sessions ->
            if (initialScreen != Screen.Login && initialScreen != Screen.Registration) runBlocking { sessions.signIn("anna") }
        })
    }
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
