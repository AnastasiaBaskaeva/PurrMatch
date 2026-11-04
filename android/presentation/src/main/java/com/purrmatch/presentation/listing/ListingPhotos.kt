package com.purrmatch.presentation.listing

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.presentation.components.designCatPhoto
import com.purrmatch.presentation.components.designUploadPhoto

private val mockListingPhoto = Regex("mock://listing/[^/]+/[0-9]+\\.jpg")

internal fun localPhotoReference(slot: Int): String =
    if (slot == -1) "local://placeholder/upload" else "local://picked/$slot"

/** Resolve transport references at the UI boundary; mock images never require network access. */
@Composable
internal fun listingPhotos(
    photos: List<ListingPhoto>,
    pickedPhotos: List<Painter> = emptyList(),
    detail: Boolean = true,
): List<Painter> {
    val cat = designCatPhoto(detail)
    val upload = designUploadPhoto()
    return photos.mapNotNull { photo ->
        when {
            photo.reference.matches(mockListingPhoto) -> cat
            photo.reference == "local://placeholder/upload" -> upload
            photo.reference.startsWith("local://picked/") ->
                photo.reference.removePrefix("local://picked/").toIntOrNull()?.let(pickedPhotos::getOrNull)
            else -> null
        }
    }
}
