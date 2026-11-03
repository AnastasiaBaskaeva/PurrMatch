package com.purrmatch.presentation.components.preview

import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.R
import com.purrmatch.presentation.components.*

/** The sample crop comes from the image frame in Figma, not from a screen export. */
@Composable
internal fun sampleCatPhoto(detail: Boolean = false): Painter {
    val original = painterResource(R.drawable.sample_cat)
    return remember(original, detail) {
        object : Painter() {
            override val intrinsicSize = Size(if (detail) 336f else 332f, if (detail) 297f else 365f)
            override fun DrawScope.onDraw() {
                val scale = size.width / intrinsicSize.width
                clipRect {
                    translate(-57f * scale, -61.01f * scale) {
                        with(original) { draw(Size(419f * scale, 476f * scale)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun sampleUploadPhoto(): Painter {
    val original = painterResource(R.drawable.sample_upload)
    return remember(original) {
        object : Painter() {
            override val intrinsicSize = Size(102f, 102f)
            override fun DrawScope.onDraw() {
                val scale = size.width / 102f
                clipRect {
                    translate(-34.5f * scale, -13f * scale) {
                        with(original) { draw(Size(171f * scale, 128f * scale)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SampleCatCard(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    CatCard(
        stringResource(R.string.preview_mia_age), stringResource(R.string.preview_breed_color), sampleCatPhoto(), stringResource(R.string.city_moscow),
        listOf(CatTag(stringResource(R.string.character_affectionate)), CatTag(stringResource(R.string.character_calm), TagStyle.Peach)),
        onClick, modifier,
    )
}

@Composable
internal fun SampleFilters(modifier: Modifier = Modifier) {
    val breedInitial = stringResource(R.string.breed_any)
    var breed by remember { mutableStateOf(breedInitial) }
    val colorInitial = stringResource(R.string.color_any)
    var color by remember { mutableStateOf(colorInitial) }
    val eyesInitial = stringResource(R.string.eyes_any)
    var eyes by remember { mutableStateOf(eyesInitial) }
    val cityInitial = stringResource(R.string.select_city)
    var city by remember { mutableStateOf(cityInitial) }
    var sex by remember { mutableIntStateOf(0) }
    var terms by remember { mutableIntStateOf(0) }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField(
            stringResource(R.string.breed_label),
            breed,
            listOf(stringResource(R.string.breed_any), stringResource(R.string.breed_british), stringResource(R.string.breed_mixed)),
            { breed = it },
        )
        ChipGroup(stringResource(R.string.sex_label), stringArrayResource(R.array.filter_sexes).toList(), sex, { sex = it })
        DropdownField(
            stringResource(R.string.color_label),
            color,
            listOf(stringResource(R.string.color_any), stringResource(R.string.color_grey), stringResource(R.string.color_red)),
            { color = it },
        )
        DropdownField(
            stringResource(R.string.eye_color_label),
            eyes,
            listOf(stringResource(R.string.eyes_any), stringResource(R.string.eyes_amber), stringResource(R.string.eyes_green)),
            { eyes = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 5.dp)) {
            Text(stringResource(R.string.age_label), style = Typography.GroupLabel, color = Colors.GroupLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    min,
                    { min = it },
                    stringResource(R.string.from_label),
                    Modifier.weight(1f),
                    stringResource(R.string.min_age_hint),
                )
                TextField(
                    max,
                    { max = it },
                    stringResource(R.string.to_label),
                    Modifier.weight(1f),
                    stringResource(R.string.max_age_hint),
                )
            }
        }
        DropdownField(
            stringResource(R.string.city_label),
            city,
            listOf(stringResource(R.string.city_moscow), stringResource(R.string.city_saint_petersburg)),
            { city = it },
        )
        ChipGroup(
            stringResource(R.string.transfer_terms),
            stringArrayResource(R.array.filter_terms).toList(),
            terms,
            { terms = it },
        )
        TextField(
            price,
            { price = it },
            stringResource(R.string.max_price_label),
            placeholder = stringResource(R.string.price_any_hint),
        )
    }
}

@Composable
internal fun SampleFields(modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf("") }
    val passwordInitial = stringResource(R.string.preview_password)
    var password by remember { mutableStateOf(passwordInitial) }
    var visible by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(true) }
    var radio by remember { mutableStateOf(true) }
    var errorValue by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(value, { value = it }, stringResource(R.string.login_label), placeholder = stringResource(R.string.login_hint))
        PasswordField(password, { password = it }, stringResource(R.string.password_label), visible, { visible = it })
        TextField(
            value,
            { value = it },
            stringResource(R.string.about_label),
            placeholder = stringResource(R.string.about_hint),
            multiline = true,
        )
        ReadOnlyField(stringResource(R.string.login_label), stringResource(R.string.preview_login))
        SelectField(stringResource(R.string.birthday_label), stringResource(R.string.birthday_hint), {}, icon = Icon.Calendar)
        TextField(
            "",
            {},
            stringResource(R.string.disabled_field),
            placeholder = stringResource(R.string.value_hint),
            enabled = false,
        )
        TextField(
            errorValue,
            { errorValue = it },
            stringResource(R.string.name_label),
            errorMessage = stringResource(R.string.name_required),
        )
        SwitchRow(stringResource(R.string.with_dogs), checked, { checked = it })
        Switch(checked, { checked = it })
        Row(horizontalArrangement = Arrangement.spacedBy(25.dp)) {
            RadioOption(stringResource(R.string.free), radio, { radio = true })
            RadioOption(stringResource(R.string.paid), !radio, { radio = false })
        }
    }
}

@Composable
internal fun SampleButtons(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Button(stringResource(R.string.sign_in), {}, Modifier.fillMaxWidth())
        Button(stringResource(R.string.publish_listing), {}, Modifier.fillMaxWidth(), ButtonStyle.Warm)
        Button(stringResource(R.string.filters), {}, Modifier.fillMaxWidth(), ButtonStyle.Secondary, leadingIcon = Icon.Filters)
        Button(stringResource(R.string.remove_favorite), {}, Modifier.fillMaxWidth(), ButtonStyle.Outlined)
        Button(stringResource(R.string.create_account), {}, Modifier.fillMaxWidth(), ButtonStyle.Text)
        Button(stringResource(R.string.sign_in), {}, Modifier.fillMaxWidth(), enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(stringResource(R.string.delete), {}, style = ButtonStyle.Destructive)
            IconButton(Icon.Back, stringResource(R.string.back), {})
            IconButton(Icon.Settings, stringResource(R.string.settings), {})
            IconButton(Icon.FavoriteFilled, stringResource(R.string.favorites), {})
            IconButton(Icon.Delete, stringResource(R.string.delete), {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContactChip(stringResource(R.string.website_label), {})
            ContactChip(stringResource(R.string.telegram_label), {})
            ContactChip(stringResource(R.string.call), {})
        }
    }
}

@Composable
internal fun SampleCards(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ProfileHeader(stringResource(R.string.preview_anna), stringResource(R.string.city_moscow))
        CatListItem(stringResource(R.string.preview_mia_age), stringResource(R.string.preview_breed_city), {}) {
            IconButton(Icon.FavoriteFilled, stringResource(R.string.remove_favorite), {})
        }
        CatListItem(stringResource(R.string.preview_sonya_age), "", {}, available = false) {
            IconButton(Icon.FavoriteFilled, stringResource(R.string.remove_favorite), {})
        }
        CatListItem(stringResource(R.string.preview_peach), stringResource(R.string.preview_price), {}) {
            IconButton(Icon.Delete, stringResource(R.string.delete_listing), {})
        }
        TagRow(listOf(CatTag(stringResource(R.string.free)), CatTag(stringResource(R.string.character_calm), TagStyle.Peach)))
        SectionHeader(stringResource(R.string.my_listings)) {
            Text(stringResource(R.string.preview_active_listings), style = Typography.Caption, color = Colors.Body)
        }
        DetailRow(stringResource(R.string.birthday_label), stringResource(R.string.preview_birthday))
        DetailRow(stringResource(R.string.color_label), stringResource(R.string.color_grey))
    }
}
