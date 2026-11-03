package com.purrmatch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.presentation.components.designCatPhoto
import com.purrmatch.presentation.components.designUploadPhoto

@Composable
internal fun listingPhotos(
    references: List<ListingPhoto>,
    pickedPhotos: List<Painter>,
    detail: Boolean = true,
): List<Painter> {
    val upload = designUploadPhoto()
    val cat = designCatPhoto()
    val detailPhoto = designCatPhoto(detail = true)
    return references.mapNotNull {
        when (it.reference) {
            "sample:upload" -> upload
            "sample:cat" -> cat
            "sample:detail" -> if (detail) detailPhoto else cat
            else -> it.reference.removePrefix("picked:").toIntOrNull()?.let(pickedPhotos::getOrNull)
        }
    }
}

@Composable
internal fun draftPhotos(slots: List<Int>, pickedPhotos: List<Painter>): List<Painter> {
    val upload = designUploadPhoto()
    return slots.mapNotNull { if (it == -1) upload else pickedPhotos.getOrNull(it) }
}
