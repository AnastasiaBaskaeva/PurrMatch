package com.purrmatch.presentation.listing

import androidx.compose.ui.res.stringArrayResource
import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.profile.displayName
import com.purrmatch.presentation.profile.Profile
import java.util.Calendar
import java.util.Locale

@Composable
fun NewListingScreen(
    state: NewListingState,
    onChange: (NewListingState) -> Unit,
    profile: Profile,
    photos: List<Painter>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ScreenLayout(stringResource(R.string.new_listing_title), modifier, onBack, navigationIcon = Icon.Close, footer = {
        ActionFooter {
            Button(
                stringResource(R.string.publish_listing),
                onPublish,
                Modifier.fillMaxWidth(),
                ButtonStyle.Warm,
            )
        }
    }) {
        FormContent {
            PhotoPicker(photos, onAddPhoto, onRemovePhoto)
            if (state.submitted && photos.isEmpty()) Body(stringResource(R.string.photo_required))
            Spacer(Modifier.height(13.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    state.name,
                    { onChange(state.copy(name = it)) },
                    stringResource(R.string.cat_name_label),
                    placeholder = stringResource(R.string.cat_name_hint),
                    errorMessage = if (state.submitted && state.name.isBlank()) stringResource(R.string.cat_name_required) else null,
                )
                DropdownField(
                    stringResource(R.string.breed_label),
                    state.breed.ifEmpty { stringResource(R.string.select_breed) },
                    breeds,
                    { onChange(state.copy(breed = it)) },
                )
                if (state.submitted && state.breed !in breeds) Body(stringResource(R.string.select_breed))
                ChipGroup(
                    stringResource(R.string.sex_label),
                    stringArrayResource(R.array.listing_sexes).toList(),
                    state.sex,
                    { onChange(state.copy(sex = it)) },
                )
                SelectField(stringResource(R.string.birthday_label), state.birthday.ifEmpty { stringResource(R.string.birthday_hint) }, {
                    val now = Calendar.getInstance()
                    DatePickerDialog(context, { _, year, month, day ->
                        onChange(state.copy(birthday = String.format(Locale.ROOT, "%02d.%02d.%04d", day, month + 1, year)))
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = now.timeInMillis
                    }.show()
                }, icon = Icon.Calendar)
                TextField(
                    state.color,
                    { onChange(state.copy(color = it)) },
                    stringResource(R.string.color_label),
                    placeholder = stringResource(R.string.color_hint),
                )
                TextField(
                    state.eyes,
                    { onChange(state.copy(eyes = it)) },
                    stringResource(R.string.eye_color_label),
                    placeholder = stringResource(R.string.eye_color_hint),
                )
                TextField(
                    state.character,
                    { onChange(state.copy(character = it)) },
                    stringResource(R.string.character_label),
                    placeholder = stringResource(R.string.character_hint),
                )
                Column(Modifier.padding(top = 5.dp)) {
                    Heading(stringResource(R.string.compatibility), Modifier.padding(bottom = 10.dp))
                    SwitchRow(stringResource(R.string.with_dogs), state.dogs, { onChange(state.copy(dogs = it)) })
                    SwitchRow(stringResource(R.string.with_cats), state.cats, { onChange(state.copy(cats = it)) })
                    SwitchRow(stringResource(R.string.with_children), state.children, { onChange(state.copy(children = it)) })
                }
                TextField(
                    state.description,
                    { onChange(state.copy(description = it)) },
                    stringResource(R.string.description_label),
                    placeholder = stringResource(R.string.description_hint),
                    multiline = true,
                )
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.transfer_terms), style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioOption(stringResource(R.string.free), !state.paid, { onChange(state.copy(paid = false)) })
                        RadioOption(stringResource(R.string.paid), state.paid, { onChange(state.copy(paid = true)) })
                    }
                }
                TextField(
                    state.price,
                    { onChange(state.copy(price = it.filter(Char::isDigit))) },
                    stringResource(R.string.paid_price_label),
                    placeholder = stringResource(R.string.zero_price_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    errorMessage = if (state.submitted && state.paid && (state.price.toLongOrNull() ?: 0) <= 0) stringResource(R.string.price_required) else null,
                )
                Notice(stringResource(R.string.listing_author_notice, profile.displayName))
            }
        }
    }
}

@Preview(name = "Новая анкета", widthDp = 376, heightDp = 803)
@Composable
private fun NewListingScreenPreview() {
    var state by remember { mutableStateOf(NewListingState(id = "preview-new-cat")) }
    ScreenPreview {
        NewListingScreen(
            state = state,
            onChange = { state = it },
            profile = ScreenPreviewData.profile,
            photos = listOf(designUploadPhoto()),
            onAddPhoto = {},
            onRemovePhoto = {},
            onBack = {},
            onPublish = {},
        )
    }
}
