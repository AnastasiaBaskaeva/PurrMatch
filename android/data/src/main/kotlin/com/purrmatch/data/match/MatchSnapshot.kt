package com.purrmatch.data.match

import java.io.File
import java.io.Serializable

/** One boundary reads all supported legacy/typed payloads, with or without credentials. */
internal fun readMatchSnapshot(file: File): StoredMatches = readCompatibleMatchSnapshot(file)

internal fun legacyMatchSnapshot(data: MatchRecords) = StoredMatches(
    data,
    data.profiles.associate { it.id to PasswordVerifier.legacyFixture },
)

internal data class StoredMatches(
    val data: MatchRecords,
    val credentials: Map<String, PasswordVerifier>,
) : Serializable {

    companion object {

        private const val serialVersionUID = 1L
    }
}
