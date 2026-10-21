package com.purrmatch.data.match

import com.purrmatch.domain.match.MatchData
import java.io.File
import java.io.Serializable

/** One boundary reads all supported legacy/typed payloads, with or without credentials. */
internal fun readMatchSnapshot(file: File): StoredMatches = readCompatibleMatchSnapshot(file)

internal fun legacyMatchSnapshot(data: MatchData) = StoredMatches(
    data, data.profiles.associate { it.id to PasswordVerifier.legacyFixture },
)

internal data class StoredMatches(
    val data: MatchData,
    val credentials: Map<String, PasswordVerifier>,
) : Serializable {
    companion object { private const val serialVersionUID = 1L }
}
