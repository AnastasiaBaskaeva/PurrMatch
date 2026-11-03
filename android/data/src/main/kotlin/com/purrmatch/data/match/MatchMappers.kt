package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.time.LocalDate

internal fun MatchRecords.toDomain() = MatchData(
    profiles.map { it.toDomain() },
    listings.map { it.toDomain() },
    selections.mapValues { it.value.toDomain() },
)

internal fun MatchData.toRecord() = MatchRecords(
    profiles.map { it.toRecord() },
    listings.map { it.toRecord() },
    selections.mapValues { it.value.toRecord() },
)

internal fun CoordinatesRecord.toDomain() = Coordinates(
    latitude,
    longitude
)

internal fun Coordinates.toRecord() = CoordinatesRecord(
    latitude,
    longitude
)

internal fun ProfileRecord.toDomain() = Profile(
    id,
    name,
    login,
    city,
    about,
    telegram,
    phone,
    website,
    surname,
    coordinates?.toDomain(),
    whatsapp,
    VerificationStatus.valueOf(verification.name),
    catteryName,
)

internal fun Profile.toRecord() = ProfileRecord(
    id,
    name,
    login,
    city,
    about,
    telegram,
    phone,
    website,
    surname,
    coordinates?.toRecord(),
    whatsapp,
    VerificationRecord.valueOf(verification.name),
    catteryName,
)

internal fun ListingRecord.toDomain() = Listing(
    id,
    name,
    authorId,
    breed,
    sex,
    birthday?.let(LocalDate::parse),
    color,
    eyes,
    character,
    description,
    city,
    price,
    photos.map(::ListingPhoto),
    coordinates?.toDomain(),
    available,
    dogs,
    cats,
    children,
)

internal fun Listing.toRecord() = ListingRecord(
    id,
    name,
    authorId,
    breed,
    sex,
    birthday?.toString(),
    color,
    eyes,
    character,
    description,
    city,
    price,
    photos.map { it.reference },
    coordinates?.toRecord(),
    available,
    dogs,
    cats,
    children,
)

internal fun FiltersRecord.toDomain() = SearchFilters(
    breed,
    sex,
    color,
    eyes,
    minAge,
    maxAge,
    city,
    terms,
    price,
)

internal fun SearchFilters.toRecord() = FiltersRecord(
    breed,
    sex,
    color,
    eyes,
    minAge,
    maxAge,
    city,
    terms,
    price,
)

internal fun SelectionRecord.toDomain() = UserSelection(
    favorites.toList(),
    seen.toList(),
    filters.toDomain()
)

internal fun UserSelection.toRecord() = SelectionRecord(
    favorites.toList(),
    seen.toList(),
    filters.toRecord()
)
