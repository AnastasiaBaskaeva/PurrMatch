package com.purrmatch.presentation

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.domain.match.Coordinates
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.domain.match.VerificationStatus
import com.purrmatch.presentation.filters.FiltersViewModel
import com.purrmatch.presentation.profile.EditProfileViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DraftRestorationTest {

    /** Force Android to marshal the draft, rather than retaining the same objects in memory. */
    private fun parcelled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun profileAndFilterDraftsRestoreWithoutSerializableDomainObjects() = runBlocking {
        val repository = MockMatchRepository()
        repository.authenticate(
            "anna_cats",
            "purrmatch-demo"
        ).getOrThrow()
        repository.saveProfile(
            repository.data.value.profile("anna")!!.copy(
                verification = VerificationStatus.PENDING,
                catteryName = "Луна",
            )
        ).getOrThrow()
        val profileHandle = SavedStateHandle()
        val profile = EditProfileViewModel(
            profileHandle,
            repository,
            "anna"
        )
        val expectedProfile = profile.state.value.copy(
            name = "Черновик",
            surname = "Иванова",
            city = "Казань",
            about = "Несохранённое описание",
            telegram = "@draft",
            phone = "+79991234567",
            website = "draft.example.com",
            whatsapp = "+79997654321",
            coordinates = Coordinates(
                55.8,
                49.1
            ),
            verification = VerificationStatus.PENDING,
            catteryName = "Луна",
        )
        profile.edit(
            expectedProfile.copy(
                id = "other",
                login = "spoofed",
                verification = VerificationStatus.APPROVED,
                catteryName = "spoofed"
            )
        )
        assertEquals(
            expectedProfile,
            profile.state.value
        )
        val restoredProfile = EditProfileViewModel(
            SavedStateHandle(
                mapOf(
                    "profile_draft" to parcelled(requireNotNull(profileHandle["profile_draft"])),
                )
            ),
            repository,
            "anna"
        )
        assertEquals(
            expectedProfile,
            restoredProfile.state.value
        )
        assertNotEquals(
            expectedProfile,
            repository.data.value.profile("anna")
        )
        val expectedFilters = SearchFilters(
            "Метис",
            2,
            "Чёрный",
            "Зелёные",
            "1",
            "7",
            "Казань",
            2,
            "5000"
        )
        val filterHandle = SavedStateHandle()
        FiltersViewModel(
            filterHandle,
            repository,
            "anna"
        ).edit(expectedFilters)
        val restoredFilters = FiltersViewModel(
            SavedStateHandle(
                mapOf(
                    "filters_draft" to parcelled(requireNotNull(filterHandle["filters_draft"])),
                )
            ),
            repository,
            "anna"
        )
        assertEquals(
            expectedFilters,
            restoredFilters.state.value
        )
        restoredFilters.reset()
        assertEquals(
            SearchFilters(),
            restoredFilters.state.value
        )
        val unknownCoordinates = expectedProfile.copy(coordinates = null)
        profile.edit(unknownCoordinates)
        assertNull(
            EditProfileViewModel(
                SavedStateHandle(
                    mapOf(
                        "profile_draft" to parcelled(requireNotNull(profileHandle["profile_draft"])),
                    )
                ),
                repository,
                "anna"
            ).state.value.coordinates
        )
    }

    @Test
    fun restoredDraftPreservesCurrentAccountAndVerification() = runBlocking {
        val repository = MockMatchRepository()
        repository.authenticate(
            "anna_cats",
            "purrmatch-demo"
        ).getOrThrow()
        val handle = SavedStateHandle()
        val model = EditProfileViewModel(
            handle,
            repository,
            "anna"
        )
        model.edit(model.state.value.copy(name = "Черновик"))
        val latest = repository.data.value.profile("anna")!!.copy(
            login = "new_login",
            verification = VerificationStatus.APPROVED,
            catteryName = "Одобренный питомник",
        )
        repository.saveProfile(latest).getOrThrow()
        val restored = EditProfileViewModel(
            SavedStateHandle(
                mapOf(
                    "profile_draft" to parcelled(requireNotNull(handle["profile_draft"])),
                )
            ),
            repository,
            "anna"
        )
        assertEquals(
            latest.copy(name = "Черновик"),
            restored.state.value
        )
    }

    @Test
    fun oldDraftProtectedFieldsAndOtherAccountDraftsAreIgnored() = runBlocking {
        val repository = MockMatchRepository()
        repository.authenticate(
            "anna_cats",
            "purrmatch-demo"
        ).getOrThrow()
        val current = repository.data.value.profile("anna")!!
        val legacyDraft = Bundle().apply {
            putString(
                "id",
                "anna"
            )
            putString(
                "name",
                "Черновик"
            )
            putString(
                "login",
                "old_login"
            )
            putString(
                "verification",
                "OUTDATED_STATUS"
            )
            putString(
                "catteryName",
                "Старый питомник"
            )
        }
        val restored = EditProfileViewModel(
            SavedStateHandle(
                mapOf(
                    "profile_draft" to parcelled(legacyDraft),
                )
            ),
            repository,
            "anna"
        )
        assertEquals(
            current.copy(name = "Черновик"),
            restored.state.value
        )
        legacyDraft.putString(
            "id",
            "olga"
        )
        val another = EditProfileViewModel(
            SavedStateHandle(
                mapOf(
                    "profile_draft" to parcelled(legacyDraft),
                )
            ),
            repository,
            "anna"
        )
        assertEquals(
            current,
            another.state.value
        )
    }

}
