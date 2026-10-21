package com.purrmatch.presentation.listing

import androidx.lifecycle.SavedStateHandle
import com.purrmatch.domain.match.MatchRepository
import com.purrmatch.presentation.AsyncActionViewModel
import java.io.Serializable
import java.util.UUID

data class NewListingState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "", val breed: String = "Выберите породу", val sex: Int = 0,
    val birthday: String = "", val color: String = "", val eyes: String = "", val character: String = "",
    val description: String = "", val dogs: Boolean = true, val cats: Boolean = true,
    val children: Boolean = true, val paid: Boolean = false, val price: String = "",
    val submitted: Boolean = false, val coverRemoved: Boolean = false,
    val removedPicked: List<Int> = emptyList(), val pickedStartIndex: Int = 0,
) : Serializable {
    fun photoSlots(pickedCount: Int) = ((if (coverRemoved) emptyList() else listOf(-1)) +
        (pickedStartIndex until pickedCount).filter { it !in removedPicked }).take(4)
}

class NewListingViewModel(
    private val savedState: SavedStateHandle,
    private val repository: MatchRepository,
    private val userId: String,
    pickedCount: Int,
) : AsyncActionViewModel() {
    val profile = requireNotNull(repository.data.value.profile(userId))
    val state = savedState.getStateFlow("listing", NewListingState(pickedStartIndex = pickedCount))
    fun edit(value: NewListingState) { savedState["listing"] = value }
    fun removePhoto(index: Int, pickedCount: Int) {
        val form = state.value
        val slot = form.photoSlots(pickedCount).getOrNull(index) ?: return
        edit(if (slot == -1) form.copy(coverRemoved = true) else form.copy(removedPicked = form.removedPicked + slot))
    }
    fun publish(pickedCount: Int, onSuccess: () -> Unit) {
        edit(state.value.copy(submitted = true))
        val form = state.value
        val photos = form.photoSlots(pickedCount)
        if (form.name.isBlank() || form.breed !in breeds || photos.isEmpty() ||
            (form.paid && (form.price.toIntOrNull() ?: 0) <= 0)) return
        runAction(onSuccess) { repository.publish(userId, Listing(form.id, form.name.trim(), authorId = userId, breed = form.breed,
            sex = if (form.sex == 0) "кошка" else "кот", age = "", birthday = form.birthday,
            color = form.color, eyes = form.eyes, character = form.character, description = form.description,
            city = profile.city, price = if (form.paid) "${form.price} ₽" else "Бесплатно",
            dogs = form.dogs, cats = form.cats, children = form.children), photos) }
    }
}
