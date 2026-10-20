package com.purrmatch.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.purrmatch.domain.session.SessionState
import com.purrmatch.presentation.R
import com.purrmatch.presentation.components.NavigationAction
import com.purrmatch.presentation.components.PlaceholderScreen
import com.purrmatch.presentation.session.SessionViewModel

@Composable
fun PurrMatchApp(modifier: Modifier = Modifier) {
    val rootController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    Surface(modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            // Start in the main area while authentication is a navigation scaffold.
            NavHost(rootController, startDestination = Main) {
                composable<Main> {
                    MainTabs(
                        session = session,
                        onLogin = { rootController.navigate(Login) },
                        onRegistration = { rootController.navigate(Registration) },
                        onSignOut = {
                            sessionViewModel.signOut()
                            rootController.navigate(Login) {
                                popUpTo<Main> { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<Login> {
                    PlaceholderScreen(
                        title = stringResource(R.string.login),
                        onBack = {
                            if (!rootController.popBackStack()) rootController.openMain()
                        },
                    ) {
                        NavigationAction(stringResource(R.string.open_registration)) {
                            rootController.navigate(Registration)
                        }
                        NavigationAction(stringResource(R.string.demo_continue)) {
                            sessionViewModel.enterDemoSession()
                            rootController.openMain()
                        }
                    }
                }
                composable<Registration> {
                    PlaceholderScreen(
                        title = stringResource(R.string.registration),
                        onBack = {
                            if (!rootController.popBackStack()) rootController.openMain()
                        },
                    ) {
                        NavigationAction(stringResource(R.string.open_login)) {
                            rootController.navigate(Login) {
                                popUpTo<Registration> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        NavigationAction(stringResource(R.string.demo_continue)) {
                            sessionViewModel.enterDemoSession()
                            rootController.openMain()
                        }
                    }
                }
            }
        }
    }
}

private fun NavHostController.openMain() {
    navigate(Main) {
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun MainTabs(
    session: SessionState,
    onLogin: () -> Unit,
    onRegistration: () -> Unit,
    onSignOut: () -> Unit,
) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showTabs = destination == null || destination.hasRoute<Home>() ||
        destination.hasRoute<Favorites>() || destination.hasRoute<Profile>()
    val back: () -> Unit = { controller.popBackStack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    NavigationBarItem(
                        selected = destination == null || destination.hasRoute<Home>(),
                        onClick = { controller.selectTab(HomeGraph) },
                        icon = {},
                        label = { Text(stringResource(R.string.home)) },
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<Favorites>() == true,
                        onClick = { controller.selectTab(FavoritesGraph) },
                        icon = {},
                        label = { Text(stringResource(R.string.favorites)) },
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<Profile>() == true,
                        onClick = { controller.selectTab(ProfileGraph) },
                        icon = {},
                        label = { Text(stringResource(R.string.profile)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = HomeGraph, modifier = Modifier.padding(padding)) {
            navigation<HomeGraph>(startDestination = Home) {
                composable<Home> {
                    PlaceholderScreen(stringResource(R.string.home)) {
                        NavigationAction(stringResource(R.string.filters)) { controller.navigate(Filters) }
                        NavigationAction(stringResource(R.string.open_cat)) {
                            controller.navigate(CatDetails("placeholder-cat"))
                        }
                    }
                }
                composable<Filters> {
                    PlaceholderScreen(stringResource(R.string.filters), onBack = back) {
                        NavigationAction(stringResource(R.string.apply_filters), onClick = back)
                        NavigationAction(stringResource(R.string.reset_filters), onClick = back)
                    }
                }
            }
            navigation<FavoritesGraph>(startDestination = Favorites) {
                composable<Favorites> {
                    PlaceholderScreen(stringResource(R.string.favorites)) {
                        NavigationAction(stringResource(R.string.open_cat)) {
                            controller.navigate(CatDetails("placeholder-cat"))
                        }
                        NavigationAction(stringResource(R.string.open_author)) {
                            controller.navigate(AuthorProfile("placeholder-author"))
                        }
                    }
                }
            }
            navigation<ProfileGraph>(startDestination = Profile) {
                composable<Profile> {
                    PlaceholderScreen(stringResource(R.string.profile)) {
                        NavigationAction(stringResource(R.string.settings)) { controller.navigate(Settings) }
                        NavigationAction(stringResource(R.string.add_cat)) { controller.navigate(CreateCat) }
                        NavigationAction(stringResource(R.string.open_cat)) {
                            controller.navigate(CatDetails("placeholder-own-cat"))
                        }
                        NavigationAction(stringResource(R.string.open_login), onClick = onLogin)
                        NavigationAction(stringResource(R.string.open_registration), onClick = onRegistration)
                    }
                }
                composable<CreateCat> {
                    PlaceholderScreen(stringResource(R.string.create_cat), onBack = back)
                }
                composable<Settings> {
                    PlaceholderScreen(stringResource(R.string.settings), onBack = back) {
                        NavigationAction(stringResource(R.string.verification)) {
                            controller.navigate(Verification)
                        }
                        if (session == SessionState.Demo) {
                            NavigationAction(stringResource(R.string.sign_out), onClick = onSignOut)
                        } else {
                            NavigationAction(stringResource(R.string.open_login), onClick = onLogin)
                        }
                    }
                }
                composable<Verification> {
                    PlaceholderScreen(stringResource(R.string.verification), onBack = back)
                }
            }
            composable<CatDetails> {
                PlaceholderScreen(stringResource(R.string.cat_details), onBack = back) {
                    NavigationAction(stringResource(R.string.open_author)) {
                        // Only IDs go into navigation; future data is loaded through the domain layer.
                        controller.navigate(AuthorProfile("placeholder-author"))
                    }
                }
            }
            composable<AuthorProfile> {
                PlaceholderScreen(stringResource(R.string.author_profile), onBack = back) {
                    NavigationAction(stringResource(R.string.open_cat)) {
                        controller.navigate(CatDetails("placeholder-cat"))
                    }
                }
            }
        }
    }
}

private fun NavHostController.selectTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
