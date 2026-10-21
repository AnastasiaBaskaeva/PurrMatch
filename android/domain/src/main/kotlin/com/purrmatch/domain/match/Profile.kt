package com.purrmatch.domain.match

import java.io.Serializable

data class Coordinates(val latitude: Double, val longitude: Double) : Serializable {
    private companion object { private const val serialVersionUID = 1L }

    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

enum class VerificationStatus { UNVERIFIED, PENDING, APPROVED, REJECTED }

data class Profile(
    val id: String = "", val name: String = "", val login: String = "", val city: String = "",
    val about: String = "", val telegram: String = "", val phone: String = "", val website: String = "",
    val surname: String = "", val coordinates: Coordinates? = null, val whatsapp: String = "",
    val verification: VerificationStatus = VerificationStatus.UNVERIFIED,
    val catteryName: String = "",
) : Serializable {
    private companion object { private const val serialVersionUID = 2L }
}
