package com.purrmatch.presentation.navigation

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.Listing
import com.purrmatch.presentation.profile.Profile as ProfileData

@Composable
internal fun PurrMatchApp(
    sessions: SessionRepository,
    matches: MatchRepository,
    modifier: Modifier = Modifier,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, ProfileData) -> Unit = { _, _ -> },
    initialDestination: NavKey? = null,
) {
    val session by sessions.session.collectAsStateWithLifecycle()
    Theme {
        Background(modifier.fillMaxSize()) {
            when (val current = session) {
                SessionState.Loading -> Unit
                SessionState.Guest -> AuthNavigation(sessions, matches, initialDestination)
                is SessionState.SignedIn -> key(current.userId) {
                    MainNavigation(sessions, matches, current.userId,
                        initialDestination?.takeUnless { it == Login || it == Registration } ?: if (current.onboarding) CompleteProfile else Home,
                        pickedPhotos, onAddPhoto, onContact)
                }
            }
        }
    }
}

@Composable
private fun AuthNavigation(sessions: SessionRepository, matches: MatchRepository, initial: NavKey?) {
    val stack = rememberNavBackStack(*if (initial == Registration) arrayOf(Login, Registration) else arrayOf(Login))
    val back = { if (stack.size > 1) stack.removeAt(stack.lastIndex); Unit }
    NavDisplay(
        backStack = stack,
        transitionSpec = { navigationTransition(isPop = false) },
        popTransitionSpec = { navigationTransition(isPop = true) },
        onBack = back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Login> {
                AuthDestination(sessions, matches, registration = false, onRegister = { stack.add(Registration) })
            }
            entry<Registration> {
                AuthDestination(sessions, matches, registration = true, onBack = back)
            }
        },
    )
}

@Composable
private fun MainNavigation(
    sessions: SessionRepository, matches: MatchRepository, userId: String, initial: NavKey,
    pickedPhotos: List<Painter>, onAddPhoto: () -> Unit, onContact: (String, ProfileData) -> Unit,
) {
    val navigation = rememberAppNavigation(initial)
    val data by matches.data.collectAsStateWithLifecycle()
    if (data.profile(userId) == null) {
        MissingDestination(stringResource(R.string.profile_unavailable), navigation::back)
        return
    }
    // Dialogs have a separate window; keep their width tied to the navigation host.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dialogWidth = maxWidth
        val dialogDensity = LocalDensity.current
        val openListing: (Listing) -> Unit = { navigation.open(ListingDetails(it.id)) }
        val dialogMetadata = DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false))
        val provider = entryProvider<NavKey> {
            entry<Home>(metadata = mapOf(TAB_ROOT to Tab.Home)) {
                HomeDestination(matches, userId, pickedPhotos, openListing, { navigation.open(Filters) })
            }
            entry<Favorites>(metadata = mapOf(TAB_ROOT to Tab.Favorites)) {
                FavoritesDestination(matches, userId, openListing, navigation::home)
            }
            entry<Profile>(metadata = mapOf(TAB_ROOT to Tab.Profile)) {
                ProfileDestination(matches, userId, false, navigation::back,
                    { navigation.open(Settings) }, openListing, { navigation.open(DeleteListing(it.id)) },
                    { navigation.open(CreateListing) }, onContact)
            }
            entry<AuthorProfile> { route ->
                ProfileDestination(matches, route.authorId, true, navigation::back,
                    { navigation.open(Settings) }, openListing, { navigation.open(DeleteListing(it.id)) },
                    { navigation.open(CreateListing) }, onContact)
            }
            entry<Filters> {
                FiltersDestination(matches, userId, navigation::back)
            }
            entry<ListingDetails> { route ->
                ListingDestination(matches, userId, route.listingId, pickedPhotos, navigation::back,
                    { navigation.open(AuthorProfile(it)) }, onContact)
            }
            entry<Settings> {
                EditProfileDestination(matches, userId, false, navigation::back, { navigation.open(Logout) })
            }
            entry<CompleteProfile> {
                EditProfileDestination(matches, userId, true, navigation::home, { navigation.open(Logout) })
            }
            entry<CreateListing> {
                CreateListingDestination(matches, userId, pickedPhotos, onAddPhoto, navigation::back, navigation::profile)
            }
            entry<DeleteListing>(metadata = dialogMetadata) { route ->
                CompositionLocalProvider(LocalDensity provides dialogDensity) {
                    DeleteListingDestination(matches, userId, route.listingId, navigation::back, Modifier.width(dialogWidth))
                }
            }
            entry<Logout>(metadata = dialogMetadata) {
                CompositionLocalProvider(LocalDensity provides dialogDensity) {
                    LogoutDestination(sessions, matches, navigation::back, Modifier.width(dialogWidth))
                }
            }
        }
        val confirmation = navigation.destination is DeleteListing || navigation.destination == Logout
        Column(Modifier.fillMaxSize().blur(if (confirmation) 1.dp else 0.dp)) {
            NavDisplay(entries = navigation.entries(provider), onBack = navigation::back,
                sceneStrategies = listOf(DialogSceneStrategy()),
                transitionSpec = { navigationTransition(isPop = false) },
                popTransitionSpec = { navigationTransition(isPop = true) },
                modifier = Modifier.weight(1f).fillMaxWidth())
            if (navigation.showsTabs) BottomBar(navigation.tab, { navigation.tab = it },
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
        }
    }
}
