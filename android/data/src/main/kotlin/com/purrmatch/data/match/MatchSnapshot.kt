package com.purrmatch.data.match

import com.purrmatch.domain.match.MatchData
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable

/** Keep legacy payload decoding at this boundary; domain snapshots retain their original format. */
internal fun readMatchSnapshot(file: File): StoredMatches = ObjectInputStream(file.inputStream()).use {
    when (val stored = it.readObject()) {
        is StoredMatches -> stored
        is MatchData -> legacyMatchSnapshot(stored)
        else -> throw IOException("Unsupported mock data")
    }
}

internal fun legacyMatchSnapshot(data: MatchData) = StoredMatches(
    data, data.profiles.associate { it.id to PasswordVerifier.legacyFixture },
)

internal data class StoredMatches(
    val data: MatchData,
    val credentials: Map<String, PasswordVerifier>,
) : Serializable {
    companion object { private const val serialVersionUID = 1L }
}
