package com.purrmatch.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class CoordinatesDto(val latitude: Double, val longitude: Double)

@Serializable
data class ProfileDto(
    val id: String = "", val name: String = "", val login: String = "", val city: String = "",
    val about: String = "", val telegram: String = "", val phone: String = "", val website: String = "",
    val surname: String = "", val coordinates: CoordinatesDto? = null, val whatsapp: String = "",
)

@Serializable
data class ListingDto(
    val id: String, val name: String, val authorId: String = "", val breed: String = "",
    val sex: String = "кошка", val birthday: String? = null,
    val color: String = "", val eyes: String = "", val character: String = "",
    val description: String = "", val city: String = "", val price: Long = 0,
    val photos: List<String> = emptyList(), val coordinates: CoordinatesDto? = null,
    val available: Boolean = true,
    val dogs: Boolean = true, val cats: Boolean = true, val children: Boolean = true,
)

@Serializable
data class ListingPageDto(
    val listings: List<ListingDto>,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
)

@Serializable
data class FiltersDto(
    val breed: String? = null, val sex: String? = null, val color: String? = null,
    val eyes: String? = null, val city: String? = null,
    val minAge: Int? = null, val maxAge: Int? = null, val maxPrice: Long? = null,
    val freeOnly: Boolean? = null,
)

@Serializable
data class SelectionDto(
    val favorites: List<String> = emptyList(),
    val seen: List<String> = emptyList(),
    val filters: FiltersDto = FiltersDto(),
)

@Serializable
data class CredentialDto(val userId: String, val login: String, val password: String)

@Serializable
data class MockFixtureDto(
    val profiles: List<ProfileDto>,
    val listings: List<ListingDto>,
    val selections: Map<String, SelectionDto> = emptyMap(),
    val credentials: List<CredentialDto> = emptyList(),
)

@Serializable
data class RegisterRequestDto(val login: String, val password: String, val name: String)

@Serializable
data class LoginRequestDto(val login: String, val password: String)

@Serializable
data class AuthResponseDto(val userId: String)

@Serializable
data class FavoriteRequestDto(val userId: String)

@Serializable
data class SwipeRequestDto(val userId: String, val like: Boolean)

@Serializable
data class SessionRequestDto(val userId: String)

@Serializable
data class SessionResponseDto(val userId: String?)

@Serializable
data class ErrorDto(val code: String, val message: String)

@Serializable
data class FailureRuleDto(
    val method: String,
    val path: String,
    val status: Int,
    val error: ErrorDto,
    val login: String? = null,
)

@Serializable
data class FailureRulesDto(val rules: List<FailureRuleDto>)
