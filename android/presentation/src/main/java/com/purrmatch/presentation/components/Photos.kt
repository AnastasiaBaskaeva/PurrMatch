package com.purrmatch.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R

@Composable
fun PhotoPicker(
    photos: List<Painter>,
    onAddPhoto: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = 4,
) {
    require(maxPhotos > 0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.photos_title, maxPhotos), style = Typography.Heading, color = Colors.Ink)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            photos.take(maxPhotos).forEachIndexed { index, photo ->
                val description = stringResource(R.string.photo_number, index + 1)
                Image(
                    photo, description,
                    Modifier.weight(1f, fill = false).size(102.dp).clip(RoundedCornerShape(17.dp))
                        .clickable(role = Role.Button) { onPhotoClick(index) },
                    contentScale = ContentScale.Crop,
                )
            }
            if (photos.size < maxPhotos) {
                val description = stringResource(R.string.add_photo)
                Box(
                    Modifier.weight(1f, fill = false).size(102.dp).clip(RoundedCornerShape(17.dp))
                        .background(Colors.Secondary.copy(alpha = .8f))
                        .drawBehind {
                            drawRoundRect(Colors.Accent, cornerRadius = CornerRadius(17.dp.toPx()),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                        }
                        .clickable(role = Role.Button, onClick = onAddPhoto)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icon.Plus, null, Modifier.size(48.dp)) }
            }
        }
        Text(stringResource(R.string.first_photo_hint), style = Typography.Caption, color = Colors.Body)
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(pageCount.coerceAtLeast(0)) { page ->
            Box(Modifier.size(11.dp).background(
                if (page == currentPage) Colors.Ink else Colors.PageInactive,
                CircleShape,
            ))
        }
    }
}

@Composable
fun PhotoGallery(
    photos: List<Painter>,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { photos.size },
    photoDescription: (Int) -> String = { "" },
) {
    if (photos.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().aspectRatio(336f / 297f).clip(RoundedCornerShape(28.dp)),
        ) { page ->
            Image(photos[page], photoDescription(page).ifEmpty { null },
                Modifier.fillMaxWidth().aspectRatio(336f / 297f), contentScale = ContentScale.Crop)
        }
        PageIndicator(photos.size, pagerState.currentPage)
    }
}
