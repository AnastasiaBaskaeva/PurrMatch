package com.purrmatch.data.backend.dto

import com.purrmatch.domain.match.Coordinates
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.ListingPage
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.domain.match.MatchData
import com.purrmatch.domain.match.Profile
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.domain.match.UserSelection
import java.time.LocalDate

fun CoordinatesDto.toDomain() = Coordinates(latitude, longitude)

fun Coordinates.toDto() = CoordinatesDto(latitude, longitude)

fun ProfileDto.toDomain() = Profile(
    id = id, name = name, login = login, city = city, about = about,
    telegram = telegram, phone = phone, website = website, surname = surname,
    coordinates = coordinates?.toDomain(), whatsapp = whatsapp,
)

fun Profile.toDto() = ProfileDto(
    id = id, name = name, login = login, city = city, about = about,
    telegram = telegram, phone = phone, website = website, surname = surname,
    coordinates = coordinates?.toDto(), whatsapp = whatsapp,
)

fun ListingDto.toDomain() = Listing(
    id = id, name = name, authorId = authorId, breed = breed, sex = sex,
    birthday = birthday?.let(LocalDate::parse), color = color, eyes = eyes,
    character = character, description = description, city = city, price = price,
    photos = photos.map(::ListingPhoto), coordinates = coordinates?.toDomain(),
    available = available, dogs = dogs, cats = cats, children = children,
)

fun Listing.toDto() = ListingDto(
    id = id, name = name, authorId = authorId, breed = breed, sex = sex,
    birthday = birthday?.toString(), color = color, eyes = eyes,
    character = character, description = description, city = city, price = price,
    photos = photos.map { it.reference }, coordinates = coordinates?.toDto(),
    available = available, dogs = dogs, cats = cats, children = children,
)

fun ListingPageDto.toDomain() = ListingPage(listings.map { it.toDomain() }, nextCursor, endReached)

fun FiltersDto.toDomain() = SearchFilters(
    breed = breed ?: "Любая порода",
    sex = when (sex) { "кошка" -> 1; "кот" -> 2; else -> 0 },
    color = color ?: "Любой окрас", eyes = eyes ?: "Любой цвет",
    minAge = minAge?.toString().orEmpty(), maxAge = maxAge?.toString().orEmpty(),
    city = city ?: "Выберите город",
    terms = when (freeOnly) { true -> 1; false -> 2; null -> 0 },
    price = maxPrice?.toString().orEmpty(),
)

fun SearchFilters.toDto() = FiltersDto(
    breed = breed.takeUnless { it == "Любая порода" },
    sex = when (sex) { 1 -> "кошка"; 2 -> "кот"; else -> null },
    color = color.takeUnless { it == "Любой окрас" },
    eyes = eyes.takeUnless { it == "Любой цвет" },
    city = city.takeUnless { it == "Выберите город" },
    minAge = minAge.toIntOrNull(), maxAge = maxAge.toIntOrNull(), maxPrice = price.toLongOrNull(),
    freeOnly = when (terms) { 1 -> true; 2 -> false; else -> null },
)

fun SelectionDto.toDomain() = UserSelection(favorites, seen, filters.toDomain())

fun UserSelection.toDto() = SelectionDto(favorites, seen, filters.toDto())

fun MockFixtureDto.toDomain() = MatchData(
    profiles = profiles.map { it.toDomain() },
    listings = listings.map { it.toDomain() },
    selections = selections.mapValues { it.value.toDomain() },
)
