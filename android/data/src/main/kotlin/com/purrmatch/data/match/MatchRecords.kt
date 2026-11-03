package com.purrmatch.data.match

import java.io.Serializable

/** Local source schema. Only mappers expose these values to domain. */
internal data class MatchRecords(
    val profiles: List<ProfileRecord>,
    val listings: List<ListingRecord>,
    val selections: Map<String, SelectionRecord> = emptyMap(),
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }

    fun selection(userId: String) = selections[userId] ?: SelectionRecord()
    fun profile(userId: String) = profiles.firstOrNull { it.id == userId }
    fun listing(listingId: String) = listings.firstOrNull { it.id == listingId }
}

internal data class CoordinatesRecord(
    val latitude: Double,
    val longitude: Double
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}

internal enum class VerificationRecord { UNVERIFIED, PENDING, APPROVED, REJECTED }

internal data class ProfileRecord(
    val id: String = "",
    val name: String = "",
    val login: String = "",
    val city: String = "",
    val about: String = "",
    val telegram: String = "",
    val phone: String = "",
    val website: String = "",
    val surname: String = "",
    val coordinates: CoordinatesRecord? = null,
    val whatsapp: String = "",
    val verification: VerificationRecord = VerificationRecord.UNVERIFIED,
    val catteryName: String = "",
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}

internal data class ListingRecord(
    val id: String,
    val name: String,
    val authorId: String = "",
    val breed: String = "",
    val sex: String = "кошка",
    val birthday: String? = null,
    val color: String = "",
    val eyes: String = "",
    val character: String = "",
    val description: String = "",
    val city: String = "",
    /** Transfer price in whole Russian rubles; zero means free. */
    val price: Long = 0,
    val photos: List<String> = emptyList(),
    val coordinates: CoordinatesRecord? = null,
    val available: Boolean = true,
    val dogs: Boolean = true,
    val cats: Boolean = true,
    val children: Boolean = true,
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}

internal data class FiltersRecord(
    val breed: String = "Любая порода",
    val sex: Int = 0,
    val color: String = "Любой окрас",
    val eyes: String = "Любой цвет",
    val minAge: String = "",
    val maxAge: String = "",
    val city: String = "Выберите город",
    val terms: Int = 0,
    val price: String = "",
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}

internal data class SelectionRecord(
    val favorites: List<String> = emptyList(),
    val seen: List<String> = emptyList(),
    val filters: FiltersRecord = FiltersRecord(),
) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}
