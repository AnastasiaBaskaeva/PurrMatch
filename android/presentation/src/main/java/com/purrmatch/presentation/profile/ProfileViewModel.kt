package com.purrmatch.presentation.profile

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileState(val profile: Profile?, val listings: List<com.purrmatch.domain.match.Listing>)

class ProfileViewModel(repository: MatchRepository, private val authorId: String) : AsyncActionViewModel() {
    private fun state(data: com.purrmatch.domain.match.MatchData) = ProfileState(
        data.profile(authorId), data.listings.filter { it.authorId == authorId && it.available },
    )
    val state = repository.data.map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(repository.data.value))
}

class EditProfileViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    private val userId: String,
) : AsyncActionViewModel() {
    private val mutableState = MutableStateFlow(
        requireNotNull(repository.data.value.profile(userId))
            .restoreProfileDraft(savedState["profile_draft"]),
    )
    val state = mutableState.asStateFlow()
    fun edit(value: Profile) {
        val next = mutableState.value.editableFieldsFrom(value)
        savedState["profile_draft"] = next.saveProfileDraft()
        mutableState.value = next
    }
    fun save(onSuccess: () -> Unit) = runAction(onSuccess) {
        val current = repository.data.value.profile(userId)
        if (current == null) Result.failure(IllegalStateException("Profile no longer exists"))
        else repository.saveProfile(current.editableFieldsFrom(state.value))
    }
}

// UI draft state uses Bundle primitives; domain models have no Android saving contract.
private fun Profile.saveProfileDraft() = Bundle().apply {
    putString("id", id)
    putString("name", name)
    putString("city", city)
    putString("about", about)
    putString("telegram", telegram)
    putString("phone", phone)
    putString("website", website)
    putString("surname", surname)
    putString("whatsapp", whatsapp)
    putBoolean("hasCoordinates", coordinates != null)
    coordinates?.let {
        putDouble("latitude", it.latitude)
        putDouble("longitude", it.longitude)
    }
}

// Identity and verification come from the current repository, never from an old form.
private fun Profile.editableFieldsFrom(draft: Profile) = copy(
    name = draft.name, surname = draft.surname, city = draft.city, about = draft.about,
    telegram = draft.telegram, phone = draft.phone, website = draft.website,
    whatsapp = draft.whatsapp, coordinates = draft.coordinates,
)

private fun Profile.restoreProfileDraft(draft: Bundle?): Profile {
    if (draft == null || draft.getString("id") != id) return this
    return copy(
        name = draft.getString("name") ?: name,
        city = draft.getString("city") ?: city,
        about = draft.getString("about") ?: about,
        telegram = draft.getString("telegram") ?: telegram,
        phone = draft.getString("phone") ?: phone,
        website = draft.getString("website") ?: website,
        surname = draft.getString("surname") ?: surname,
        whatsapp = draft.getString("whatsapp") ?: whatsapp,
        coordinates = when {
            draft.containsKey("latitude") && draft.containsKey("longitude") ->
                com.purrmatch.domain.match.Coordinates(draft.getDouble("latitude"), draft.getDouble("longitude"))
            draft.containsKey("hasCoordinates") && !draft.getBoolean("hasCoordinates") -> null
            else -> coordinates
        },
    )
}
