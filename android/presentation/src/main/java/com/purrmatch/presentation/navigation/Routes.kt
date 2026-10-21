package com.purrmatch.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable internal data object Login : NavKey
@Serializable internal data object Registration : NavKey
@Serializable internal data object Home : NavKey
@Serializable internal data object Favorites : NavKey
@Serializable internal data object Profile : NavKey
@Serializable internal data object Filters : NavKey
@Serializable internal data class ListingDetails(
    val listingId: String,
    val entryId: String = UUID.randomUUID().toString(),
) : NavKey
@Serializable internal data class AuthorProfile(
    val authorId: String,
    val entryId: String = UUID.randomUUID().toString(),
) : NavKey
@Serializable internal data object CreateListing : NavKey
@Serializable internal data object Settings : NavKey
@Serializable internal data object CompleteProfile : NavKey
@Serializable internal data class DeleteListing(val listingId: String) : NavKey
@Serializable internal data object Logout : NavKey
