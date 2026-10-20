package com.purrmatch.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable internal data object Main
@Serializable internal data object Login
@Serializable internal data object Registration
@Serializable internal data object HomeGraph
@Serializable internal data object FavoritesGraph
@Serializable internal data object ProfileGraph
@Serializable internal data object Home
@Serializable internal data object Favorites
@Serializable internal data object Profile
@Serializable internal data object Filters
@Serializable internal data class CatDetails(val catId: String)
@Serializable internal data class AuthorProfile(val authorId: String)
@Serializable internal data object CreateCat
@Serializable internal data object Settings
@Serializable internal data object Verification
