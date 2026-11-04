package com.purrmatch.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import com.purrmatch.presentation.auth.*
import com.purrmatch.presentation.browse.*
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.favorites.*
import com.purrmatch.presentation.filters.*
import com.purrmatch.presentation.listing.*
import com.purrmatch.presentation.profile.EditProfileScreen
import com.purrmatch.presentation.profile.EditProfileViewModel
import com.purrmatch.presentation.profile.ProfileScreen
import com.purrmatch.presentation.profile.displayName
import com.purrmatch.presentation.profile.ProfileViewModel
import com.purrmatch.presentation.profile.Profile as ProfileData
import com.purrmatch.presentation.session.SessionViewModel

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
                SessionState.Loading -> StateContent(ScreenState.Loading, Modifier.fillMaxSize())
                SessionState.Guest -> AuthNavigation(sessions, matches, initialDestination)
                is SessionState.SignedIn -> key(current.userId) {
                    // The user's own profile and selection are loaded before the main screens appear.
                    var attempt by remember { mutableIntStateOf(0) }
                    var state by remember {
                        mutableStateOf<ScreenState>(
                            if (matches.data.value.profile(current.userId) != null) ScreenState.Content else ScreenState.Loading)
                    }
                    LaunchedEffect(attempt) {
                        val result = matches.load(current.userId)
                        if (state !is ScreenState.Content) state = result.fold(
                            onSuccess = { ScreenState.Content },
                            onFailure = { ScreenState.Error("Не удалось загрузить данные. Повторите попытку.") },
                        )
                    }
                    StateContent(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { state = ScreenState.Loading; attempt++ },
                    ) {
                        MainNavigation(sessions, matches, current.userId,
                            initialDestination?.takeUnless { it == Login || it == Registration } ?: if (current.onboarding) CompleteProfile else Home,
                            pickedPhotos, onAddPhoto, onContact)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthNavigation(sessions: SessionRepository, matches: MatchRepository, initial: NavKey?) {
    val stack = rememberNavBackStack(*if (initial == Registration) arrayOf(Login, Registration) else arrayOf(Login))
    NavDisplay(
        backStack = stack,
        transitionSpec = { navigationTransition(isPop = false) },
        popTransitionSpec = { navigationTransition(isPop = true) },
        onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Login> {
                val model = screenModel { AuthViewModel(it, matches, sessions) }
                val state by model.state.collectAsStateWithLifecycle()
                val error by model.error.collectAsStateWithLifecycle()
                val loading by model.loading.collectAsStateWithLifecycle()
                androidx.activity.compose.BackHandler(enabled = loading) {}
                LoginScreen(state, model::edit, { model.submit(false) }, { stack.add(Registration) }, loading = loading, error = error)
            }
            entry<Registration> {
                val model = screenModel { AuthViewModel(it, matches, sessions) }
                val state by model.state.collectAsStateWithLifecycle()
                val error by model.error.collectAsStateWithLifecycle()
                val loading by model.loading.collectAsStateWithLifecycle()
                androidx.activity.compose.BackHandler(enabled = loading) {}
                RegistrationScreen(state, model::edit, { if (!loading) stack.removeAt(stack.lastIndex) }, { model.submit(true) }, loading = loading, error = error)
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
    val openListing: (Listing) -> Unit = { navigation.open(ListingDetails(it.id)) }
    val catPhoto = designCatPhoto()
    val detailPhoto = designCatPhoto(detail = true)
    val provider = entryProvider<NavKey> {
        entry<Home>(metadata = mapOf(TAB_ROOT to Tab.Home)) {
            val model = screenModel { BrowseViewModel(matches, userId) }
            val state by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val current = state.current
            val pending by model.pendingSwipe.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { model.reload() }
            when {
                current == null && state.loading -> StateContent(ScreenState.Loading, Modifier.fillMaxSize())
                current == null && state.failed -> StateContent(
                    ScreenState.Error("Не удалось загрузить анкеты. Повторите попытку."),
                    Modifier.fillMaxSize(), onRetry = model::reload,
                )
                else -> Box { HomeScreen(current, listingPhotos(current?.photos.orEmpty(), pickedPhotos, detail = false).firstOrNull() ?: catPhoto,
                    { current?.let { matches.remember(it); openListing(it) } }, {}, {}, { navigation.open(Filters) },
                    onSwipe = { like, complete -> current?.let { model.swipe(it.id, like, complete) } },
                    pendingLike = pending?.takeIf { it.listingId == current?.id }?.like); ActionError(error, model::dismissError) }
            }
        }
        entry<Favorites>(metadata = mapOf(TAB_ROOT to Tab.Favorites)) {
            val model = screenModel { FavoritesViewModel(matches, userId) }
            val state by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            Box {
                if (state.loading && state.listings.isEmpty()) StateContent(ScreenState.Loading, Modifier.fillMaxSize())
                else FavoritesScreen(state.listings, openListing, { model.toggle(it.id) }, navigation::home)
                ActionError(error, model::dismissError)
            }
        }
        entry<Profile>(metadata = mapOf(TAB_ROOT to Tab.Profile)) {
            ProfileDestination(matches, userId, false, navigation, openListing, onContact)
        }
        entry<AuthorProfile> { route ->
            ProfileDestination(matches, route.authorId, true, navigation, openListing, onContact)
        }
        entry<com.purrmatch.presentation.navigation.Filters> {
            val model = screenModel { FiltersViewModel(matches, userId) }
            val filters by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            Box { FiltersScreen(filters, model::edit, model::reset, navigation::back, { model.apply(navigation::back) }); ActionError(error, model::dismissError) }
        }
        entry<ListingDetails> { route ->
            val model = screenModel { ListingViewModel(matches, userId, route.listingId) }
            val state by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val listing = state.listing
            val author = state.author
            if (listing == null && state.loading) StateContent(ScreenState.Loading, Modifier.fillMaxSize())
            else if (listing == null) MissingDestination("Анкета не найдена", navigation::back)
            else if (listing.available && author == null && !state.loading) MissingDestination("Автор не найден", navigation::back)
            else Box { ListingScreen(listing, author?.displayName.orEmpty(), listingPhotos(listing.photos, pickedPhotos).ifEmpty { List(3) { detailPhoto } },
                state.favorite, navigation::back,
                { model.toggleFavorite { if (!listing.available) navigation.back() } },
                { navigation.open(AuthorProfile(listing.authorId)) },
                { type -> state.author?.let { onContact(type, it) } }); ActionError(error, model::dismissError) }
        }
        entry<Settings> { EditDestination(matches, userId, false, navigation) }
        entry<CompleteProfile> { EditDestination(matches, userId, true, navigation) }
        entry<CreateListing> {
            val model = screenModel { NewListingViewModel(it, matches, userId, pickedPhotos.size) }
            val state by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            Box { NewListingScreen(state, model::edit, model.profile, listingPhotos(state.photoSlots(pickedPhotos.size).map { ListingPhoto(localPhotoReference(it)) }, pickedPhotos),
                onAddPhoto, { model.removePhoto(it, pickedPhotos.size) }, navigation::back,
                { model.publish(pickedPhotos.size, navigation::profile) }); ActionError(error, model::dismissError) }
        }
        entry<DeleteListing>(metadata = mapOf(CONFIRMATION to true)) { route ->
            val model = screenModel { ListingViewModel(matches, userId, route.listingId) }
            val state by model.state.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val listing = state.listing
            Box { ConfirmationDestination(Confirmation.DeleteListing, navigation::back,
                { model.delete(navigation::back) }) {
                ScreenLayout("Профиль") {
                    FormContent(Modifier.blur(1.dp)) {
                        listing?.let { CatListItem(it.name, "${it.priceText} · опубликована", {}) }
                    }
                }
            }; ActionError(error, model::dismissError) }
        }
        entry<Logout>(metadata = mapOf(CONFIRMATION to true)) {
            val model = screenModel { SessionViewModel(sessions) }
            val error by model.error.collectAsStateWithLifecycle()
            val profileModel = screenModel { ProfileViewModel(matches, userId) }
            val state by profileModel.state.collectAsStateWithLifecycle()
            val profile = state.profile
            Box { ConfirmationDestination(Confirmation.Logout, navigation::back, model::signOut) {
                ScreenLayout("Настройки") {
                    FormContent(Modifier.blur(1.dp)) {
                        Heading("Данные аккаунта"); Spacer(Modifier.height(9.dp))
                        ReadOnlyField("Логин", profile?.login.orEmpty())
                        Spacer(Modifier.height(24.dp)); TextField(profile?.name.orEmpty(), {}, "Имя")
                    }
                }
            }; ActionError(error, model::dismissError) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        NavDisplay(entries = navigation.entries(provider), onBack = navigation::back,
            transitionSpec = { navigationTransition(isPop = false) },
            popTransitionSpec = { navigationTransition(isPop = true) },
            modifier = Modifier.weight(1f).fillMaxWidth())
        if (navigation.showsTabs) BottomBar(navigation.tab, { navigation.tab = it },
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@Composable
private fun ProfileDestination(
    matches: MatchRepository, authorId: String, public: Boolean, navigation: AppNavigationState,
    onOpen: (Listing) -> Unit, onContact: (String, ProfileData) -> Unit,
) {
    val model = screenModel { ProfileViewModel(matches, authorId) }
    val state by model.state.collectAsStateWithLifecycle()
    val profile = state.profile
    if (profile == null && state.loading) StateContent(ScreenState.Loading, Modifier.fillMaxSize())
    else if (profile == null) MissingDestination("Автор не найден", navigation::back)
    else ProfileScreen(profile, state.listings, public, navigation::back,
        { navigation.open(Settings) }, onOpen, { navigation.open(DeleteListing(it.id)) },
        { navigation.open(CreateListing) }, { onContact(it, profile) })
}

@Composable
private fun EditDestination(matches: MatchRepository, userId: String, onboarding: Boolean, navigation: AppNavigationState) {
    val model = screenModel { EditProfileViewModel(it, matches, userId) }
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val back = { if (onboarding) navigation.home() else navigation.back() }
    Box { EditProfileScreen(state, onboarding, model::edit, { model.save(back) }, back, { navigation.open(Logout) }); ActionError(error, model::dismissError) }
}

@Composable
private fun ConfirmationDestination(
    confirmation: Confirmation, onBack: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content()
        ConfirmationDialog(confirmation, onConfirm, onBack, Modifier.width(maxWidth))
    }
}

@Composable
private fun MissingDestination(message: String, onBack: () -> Unit) {
    ScreenLayout(message, onBack = onBack) { FormContent { Body(message) } }
}

@Composable
private fun ActionError(error: String?, dismiss: () -> Unit) {
    if (error != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        title = { androidx.compose.material3.Text("Не удалось сохранить") },
        text = { androidx.compose.material3.Text(error) },
        confirmButton = { androidx.compose.material3.TextButton(dismiss) { androidx.compose.material3.Text("Понятно") } },
    )
}

@Composable
private inline fun <reified T : ViewModel> screenModel(crossinline create: (SavedStateHandle) -> T): T =
    viewModel(factory = viewModelFactory { initializer { create(createSavedStateHandle()) } })
