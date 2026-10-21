package com.purrmatch.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.auth.LoginScreen
import com.purrmatch.presentation.auth.RegistrationScreen
import com.purrmatch.presentation.browse.HomeScreen
import com.purrmatch.presentation.favorites.FavoritesScreen
import com.purrmatch.presentation.filters.Filters
import com.purrmatch.presentation.filters.FiltersScreen
import com.purrmatch.presentation.listing.Listing
import com.purrmatch.presentation.listing.ListingScreen
import com.purrmatch.presentation.listing.NewListingScreen
import com.purrmatch.presentation.listing.designListings
import com.purrmatch.presentation.profile.EditProfileScreen
import com.purrmatch.presentation.profile.Profile
import com.purrmatch.presentation.profile.ProfileScreen

/** All Figma screens can be opened directly by the debug host's `screen` intent extra. */
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

private val filtersSaver = listSaver<Filters, Any>(
    save = { listOf(it.breed, it.sex, it.color, it.eyes, it.minAge, it.maxAge, it.city, it.terms, it.price) },
    restore = { values -> Filters().apply {
        breed = values[0] as String; sex = values[1] as Int; color = values[2] as String; eyes = values[3] as String
        minAge = values[4] as String; maxAge = values[5] as String; city = values[6] as String; terms = values[7] as Int; price = values[8] as String
    } },
)

/** Local interactive UI host. Backend authentication, persistence and delivery are outside this UI module. */
@Composable
fun App(
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen.Login,
    pickedPhotos: List<Painter> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onContact: (String, Profile) -> Unit = { _, _ -> },
) {
    var route by rememberSaveable { mutableStateOf(initialScreen.route) }
    var history by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var profile by rememberSaveable { mutableStateOf(Profile()) }
    var profileEdited by rememberSaveable { mutableStateOf(false) }
    var listings by rememberSaveable { mutableStateOf<List<Listing>>(designListings) }
    var ownIds by rememberSaveable { mutableStateOf<List<String>>(listOf("mia", "peach")) }
    var favorites by rememberSaveable { mutableStateOf<List<String>>(if (initialScreen == Screen.EmptyFavorites) arrayListOf<String>() else arrayListOf("mia", "peach", "sonya")) }
    var seen by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf(if (initialScreen == Screen.Unavailable) "sonya" else "mia") }
    var pendingDeletion by rememberSaveable { mutableStateOf<String?>(if (initialScreen == Screen.Delete) "mia" else null) }
    var logout by rememberSaveable { mutableStateOf(initialScreen == Screen.Logout) }
    var coverRemoved by rememberSaveable { mutableStateOf(false) }
    var removedPicked by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var photoSlots by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val filters = rememberSaveable(saver = filtersSaver) { Filters() }
    val screen = Screen.fromRoute(route)
    fun navigate(target: Screen) { history = ArrayList(history + route); route = target.route }
    fun replace(target: Screen) { route = target.route; history = arrayListOf() }
    fun back() {
        if (pendingDeletion != null) pendingDeletion = null
        else if (logout) logout = false
        else if (history.isNotEmpty()) { route = history.last(); history = ArrayList(history.dropLast(1)) }
        else replace(if (screen in listOf(Screen.Registration, Screen.Login)) Screen.Login else Screen.Home)
    }
    BackHandler(enabled = pendingDeletion != null || logout || screen != Screen.Login && screen != Screen.Home) { back() }
    val catPhoto = designCatPhoto()
    val detailPhoto = designCatPhoto(detail = true)
    val uploadPhoto = designUploadPhoto()
    val formPhotos = ((if (coverRemoved) emptyList() else listOf(uploadPhoto)) + pickedPhotos.filterIndexed { i, _ -> i !in removedPicked }).take(4)
    fun photosFor(id: String): List<Painter> = photoSlots.firstOrNull { it.substringBefore('|') == id }
        ?.substringAfter('|')?.split(',')?.mapNotNull { value ->
            val slot = value.toIntOrNull()
            if (slot == -1) uploadPhoto else slot?.let { pickedPhotos.getOrNull(it) }
        }.orEmpty()
    val selected = listings.firstOrNull { it.id == selectedId } ?: designListings.first()
    val current = listings.firstOrNull { listing ->
        listing.available && listing.id !in seen && (filters.breed == "Любая порода" || filters.breed == listing.breed) &&
            (filters.sex == 0 || (filters.sex == 1) == (listing.sex == "кошка")) &&
            (filters.color == "Любой окрас" || filters.color.equals(listing.color, true)) &&
            (filters.eyes == "Любой цвет" || filters.eyes.equals(listing.eyes, true)) &&
            (filters.city == "Выберите город" || filters.city == listing.city) &&
            (filters.terms == 0 || (filters.terms == 1) == (listing.price == "Бесплатно")) &&
            (filters.price.toIntOrNull() == null || (listing.price.filter(Char::isDigit).toIntOrNull() ?: 0) <= filters.price.toInt()) &&
            (filters.minAge.toIntOrNull() == null || (if (listing.age.contains("мес")) 0 else listing.age.filter(Char::isDigit).toIntOrNull() ?: 0) >= filters.minAge.toInt()) &&
            (filters.maxAge.toIntOrNull() == null || (if (listing.age.contains("мес")) 0 else listing.age.filter(Char::isDigit).toIntOrNull() ?: 0) <= filters.maxAge.toInt())
    }
    fun open(listing: Listing) { selectedId = listing.id; navigate(Screen.Listing) }
    fun toggleFavorite(id: String) { favorites = ArrayList(if (id in favorites) favorites - id else favorites + id) }
    val tab = when (screen) {
        Screen.Favorites, Screen.EmptyFavorites -> Tab.Favorites
        Screen.Profile -> Tab.Profile
        else -> Tab.Home
    }
    val showTabs = screen in listOf(Screen.Home, Screen.NoResults, Screen.SwipeRight,
        Screen.SwipeLeft, Screen.Favorites, Screen.EmptyFavorites, Screen.Profile)
    Theme {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val viewportWidth = maxWidth
            Background(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        key(route) {
                            when {
                                pendingDeletion != null -> ScreenLayout("Профиль") {
                                    FormContent(Modifier.blur(1.dp)) {
                                        val listing = listings.firstOrNull { it.id == pendingDeletion } ?: selected
                                        CatListItem(listing.name, "${listing.price} · опубликована", {})
                                    }
                                }
                                logout -> ScreenLayout("Настройки") {
                                    FormContent(Modifier.blur(1.dp)) {
                                        Heading("Данные аккаунта"); Spacer(Modifier.height(9.dp))
                                        ReadOnlyField("Логин", profile.login)
                                        Spacer(Modifier.height(24.dp)); TextField(profile.name, {}, "Имя")
                                    }
                                }
                                else -> when (screen) {
                                    Screen.Login -> LoginScreen({ replace(Screen.Home) }, { navigate(Screen.Registration) })
                                    Screen.Registration -> RegistrationScreen(::back, { name, login ->
                                        profile = profile.copy(name = name, login = login); replace(Screen.CompleteProfile)
                                    })
                                    Screen.CompleteProfile, Screen.Settings, Screen.Logout -> EditProfileScreen(
                                        profile, screen == Screen.CompleteProfile,
                                        { profile = it; profileEdited = true; replace(if (screen == Screen.CompleteProfile) Screen.Home else Screen.Profile) },
                                        { if (screen == Screen.CompleteProfile) replace(Screen.Home) else back() }, { logout = true },
                                        initialAbout = if (profileEdited) profile.about else "Помогаю кошкам находить дом")
                                    Screen.Home, Screen.NoResults, Screen.SwipeRight, Screen.SwipeLeft -> HomeScreen(
                                        if (screen == Screen.NoResults) null else current,
                                        current?.let { photosFor(it.id).firstOrNull() } ?: catPhoto,
                                        { current?.let(::open) }, {
                                            current?.let { if (it.id !in favorites) favorites = ArrayList(favorites + it.id); seen = ArrayList(seen + it.id) }
                                        }, { current?.let { seen = ArrayList(seen + it.id) } }, { navigate(Screen.Filters) },
                                        swipeProgress = when (screen) { Screen.SwipeRight -> .33f; Screen.SwipeLeft -> -.33f; else -> null })
                                    Screen.Filters -> FiltersScreen(filters, ::back, { seen = arrayListOf(); replace(Screen.Home) })
                                    Screen.Favorites, Screen.EmptyFavorites -> FavoritesScreen(
                                        listings.filter { it.id in favorites }, ::open, { toggleFavorite(it.id) }, { replace(Screen.Home) })
                                    Screen.Profile, Screen.Author, Screen.Delete -> ProfileScreen(
                                        profile,
                                        listings.filter { it.id in ownIds && it.available }, screen == Screen.Author,
                                        ::back, { navigate(Screen.Settings) }, ::open, { pendingDeletion = it.id },
                                        { coverRemoved = false; removedPicked = arrayListOf(); navigate(Screen.NewListing) },
                                        { onContact(it, profile) })
                                    Screen.NewListing -> NewListingScreen(profile, formPhotos, onAddPhoto, { index ->
                                        if (!coverRemoved && index == 0) coverRemoved = true
                                        else {
                                            val visible = pickedPhotos.indices.filter { it !in removedPicked }
                                            val actual = visible.getOrNull(index - if (coverRemoved) 0 else 1)
                                            actual?.let { removedPicked = ArrayList(removedPicked + it) }
                                        }
                                    }, ::back, { listing ->
                                        listings = ArrayList(listings + listing); ownIds = ArrayList(ownIds + listing.id)
                                        val slots = ((if (coverRemoved) emptyList() else listOf(-1)) + pickedPhotos.indices.filter { it !in removedPicked }).take(4)
                                        photoSlots = photoSlots + "${listing.id}|${slots.joinToString(",")}"
                                        replace(Screen.Profile)
                                    })
                                    Screen.Listing, Screen.Unavailable -> ListingScreen(selected,
                                        photosFor(selected.id).ifEmpty { List(3) { detailPhoto } }, selected.id in favorites, ::back,
                                        { toggleFavorite(selected.id); if (!selected.available) replace(Screen.Favorites) },
                                        { navigate(Screen.Author) }, { onContact(it, profile) })
                                }
                            }
                        }
                    }
                    if (showTabs && pendingDeletion == null && !logout) BottomBar(tab, {
                        replace(when (it) { Tab.Home -> Screen.Home; Tab.Favorites -> Screen.Favorites; Tab.Profile -> Screen.Profile })
                    }, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                }
                if (pendingDeletion != null || logout) ConfirmationDialog(
                    if (logout) Confirmation.Logout else Confirmation.DeleteListing,
                    onConfirm = {
                        if (logout) {
                            logout = false; favorites = arrayListOf(); seen = arrayListOf(); replace(Screen.Login)
                        } else {
                            val id = pendingDeletion
                            listings = ArrayList(listings.map { if (it.id == id) it.copy(available = false) else it })
                            ownIds = ArrayList(ownIds.filter { it != id }); pendingDeletion = null; replace(Screen.Profile)
                        }
                    }, onDismiss = {
                        pendingDeletion = null
                        logout = false
                        if (screen == Screen.Delete) replace(Screen.Profile)
                        else if (screen == Screen.Logout) replace(Screen.Settings)
                    },
                    modifier = Modifier.width(viewportWidth),
                )
            }
        }
    }
}

@Preview(name = "Подтверждение удаления", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun DeleteListingScreenPreview() {
    App(initialScreen = Screen.Delete)
}

@Preview(name = "Подтверждение выхода", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun LogoutScreenPreview() {
    App(initialScreen = Screen.Logout)
}
