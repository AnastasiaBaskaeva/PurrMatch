package com.purrmatch.presentation.navigation

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.presentation.auth.*
import com.purrmatch.presentation.browse.*
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.favorites.*
import com.purrmatch.presentation.filters.FiltersScreen
import com.purrmatch.presentation.filters.FiltersViewModel
import com.purrmatch.presentation.listing.*
import com.purrmatch.presentation.profile.EditProfileScreen
import com.purrmatch.presentation.profile.EditProfileViewModel
import com.purrmatch.presentation.profile.ProfileScreen
import com.purrmatch.presentation.profile.ProfileViewModel
import com.purrmatch.presentation.session.SessionViewModel
import com.purrmatch.presentation.profile.Profile as ProfileData

@Composable
internal fun AuthDestination(
    sessions: SessionRepository,
    matches: MatchRepository,
    registration: Boolean,
    onBack: () -> Unit = {},
    onRegister: () -> Unit = {},
) {
    val model = screenModel { AuthViewModel(it, matches, sessions) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    BackHandler(enabled = loading) {}
    if (registration) {
        RegistrationScreen(state, model::edit, { if (!loading) onBack() },
            { model.submit(true) }, loading = loading, error = error?.let { stringResource(it) })
    } else {
        LoginScreen(state, model::edit, { model.submit(false) }, onRegister,
            loading = loading, error = error?.let { stringResource(it) })
    }
}

@Composable
internal fun HomeDestination(
    matches: MatchRepository,
    userId: String,
    pickedPhotos: List<Painter>,
    onOpen: (Listing) -> Unit,
    onFilters: () -> Unit,
) {
    val model = screenModel { BrowseViewModel(matches, userId) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val current = state.current
    val photo = listingPhotos(current?.photos.orEmpty(), pickedPhotos, detail = false).firstOrNull()
        ?: designCatPhoto()
    Box {
        HomeScreen(current, photo, { current?.let(onOpen) },
            { current?.let { model.swipe(it.id, true) } },
            { current?.let { model.swipe(it.id, false) } }, onFilters)
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun FavoritesDestination(
    matches: MatchRepository,
    userId: String,
    onOpen: (Listing) -> Unit,
    onHome: () -> Unit,
) {
    val model = screenModel { FavoritesViewModel(matches, userId) }
    val listings by model.listings.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box {
        FavoritesScreen(listings, onOpen, { model.toggle(it.id) }, onHome)
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun FiltersDestination(matches: MatchRepository, userId: String, onBack: () -> Unit) {
    val model = screenModel { FiltersViewModel(it, matches, userId) }
    val filters by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box {
        FiltersScreen(filters, model::edit, model::reset, onBack, { model.apply(onBack) })
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun ListingDestination(
    matches: MatchRepository,
    userId: String,
    listingId: String,
    pickedPhotos: List<Painter>,
    onBack: () -> Unit,
    onAuthor: (String) -> Unit,
    onContact: (String, ProfileData) -> Unit,
) {
    val model = screenModel { ListingViewModel(matches, userId, listingId) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val listing = state.listing
    if (listing == null) {
        MissingDestination(stringResource(R.string.listing_not_found), onBack)
    } else {
        val photos = listingPhotos(listing.photos, pickedPhotos)
        val detailPhoto = designCatPhoto(detail = true)
        Box {
            ListingScreen(listing, state.author, photos.ifEmpty { List(3) { detailPhoto } },
                state.favorite, onBack,
                { model.toggleFavorite { if (!listing.available) onBack() } },
                { onAuthor(listing.authorId) },
                { type -> state.author?.let { onContact(type, it) } })
            ActionError(error, model::dismissError)
        }
    }
}

@Composable
internal fun CreateListingDestination(
    matches: MatchRepository,
    userId: String,
    pickedPhotos: List<Painter>,
    onAddPhoto: () -> Unit,
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    val breeds = breeds
    val model = screenModel { NewListingViewModel(it, matches, userId, pickedPhotos.size, breeds) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box {
        NewListingScreen(state, model::edit, model.profile, draftPhotos(state.photoSlots(pickedPhotos.size), pickedPhotos),
            onAddPhoto, { model.removePhoto(it, pickedPhotos.size) }, onBack,
            { model.publish(pickedPhotos.size, onPublished) })
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun ProfileDestination(
    matches: MatchRepository,
    authorId: String,
    public: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (Listing) -> Unit,
    onDelete: (Listing) -> Unit,
    onCreate: () -> Unit,
    onContact: (String, ProfileData) -> Unit,
) {
    val model = screenModel { ProfileViewModel(matches, authorId) }
    val state by model.state.collectAsStateWithLifecycle()
    val profile = state.profile
    if (profile == null) MissingDestination(stringResource(R.string.author_not_found), onBack)
    else ProfileScreen(profile, state.listings, public, onBack, onSettings, onOpen, onDelete, onCreate,
        { onContact(it, profile) })
}

@Composable
internal fun EditProfileDestination(
    matches: MatchRepository,
    userId: String,
    onboarding: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val model = screenModel { EditProfileViewModel(it, matches, userId) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box {
        EditProfileScreen(state, onboarding, model::edit, { model.save(onBack) }, onBack, onLogout)
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun DeleteListingDestination(
    matches: MatchRepository,
    userId: String,
    listingId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = screenModel { ListingViewModel(matches, userId, listingId) }
    val error by model.error.collectAsStateWithLifecycle()
    Box(modifier) {
        ConfirmationDialogContent(Confirmation.DeleteListing, { model.delete(onBack) }, onBack)
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun LogoutDestination(
    sessions: SessionRepository,
    matches: MatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = screenModel { SessionViewModel(sessions, matches) }
    val error by model.error.collectAsStateWithLifecycle()
    Box(modifier) {
        ConfirmationDialogContent(Confirmation.Logout, model::signOut, onBack)
        ActionError(error, model::dismissError)
    }
}

@Composable
internal fun MissingDestination(message: String, onBack: () -> Unit) {
    ScreenLayout(message, onBack = onBack) { FormContent { Body(message) } }
}

@Composable
private fun ActionError(error: Int?, dismiss: () -> Unit) {
    if (error != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.save_failed_title)) },
        text = { androidx.compose.material3.Text(stringResource(error)) },
        confirmButton = { androidx.compose.material3.TextButton(dismiss) { androidx.compose.material3.Text(stringResource(R.string.understood)) } },
    )
}

@Composable
private inline fun <reified T : ViewModel> screenModel(crossinline create: (SavedStateHandle) -> T): T =
    viewModel(factory = viewModelFactory { initializer { create(createSavedStateHandle()) } })
