package com.purrmatch.presentation.profile

import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import com.purrmatch.presentation.preview.ScreenPreviewData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*

@Composable
fun EditProfileScreen(
    profile: Profile,
    onboarding: Boolean,
    onChange: (Profile) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenLayout(if (onboarding) stringResource(R.string.about_you_title) else stringResource(R.string.settings), modifier,
        onBack = if (onboarding) null else onBack, footer = {
            ActionFooter {
                Button(
                    if (onboarding) stringResource(R.string.save_profile) else stringResource(R.string.save_changes),
                    onSave,
                    Modifier.fillMaxWidth(),
                    ButtonStyle.Warm,
                )
                if (onboarding) Button(stringResource(R.string.complete_later), onBack,
                    Modifier.fillMaxWidth().padding(top = 11.dp), ButtonStyle.Text)
            }
        }) {
        FormContent {
            if (onboarding) {
                Notice(stringResource(R.string.profile_contact_notice),
                    backgroundColor = Colors.Like)
                Spacer(Modifier.height(24.dp))
            } else {
                Heading(stringResource(R.string.account_data))
                Spacer(Modifier.height(9.dp))
                ReadOnlyField(stringResource(R.string.login_label), profile.login, backgroundColor = Colors.SettingsReadOnly)
                Spacer(Modifier.height(12.dp))
                TextField(
                    profile.name,
                    { onChange(profile.copy(name = it)) },
                    stringResource(R.string.name_label),
                    textColor = Colors.EnteredText,
                )
                Spacer(Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    profile.city,
                    { onChange(profile.copy(city = it)) },
                    if (onboarding) stringResource(R.string.city_district_label) else stringResource(R.string.city_label),
                    placeholder = stringResource(R.string.location_hint),
                    textColor = if (onboarding) Colors.Muted else Colors.EnteredText,
                )
                TextField(
                    profile.about,
                    { onChange(profile.copy(about = it)) },
                    stringResource(R.string.about_label),
                    placeholder = stringResource(R.string.about_hint),
                    multiline = true,
                )
            }
            if (!onboarding) {
                Heading(stringResource(R.string.author_contacts), Modifier.padding(top = 24.dp, bottom = 9.dp))
            } else Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    profile.telegram,
                    { onChange(profile.copy(telegram = it)) },
                    stringResource(R.string.telegram_label),
                    placeholder = stringResource(R.string.username_hint),
                )
                TextField(
                    profile.phone,
                    { onChange(profile.copy(phone = it)) },
                    stringResource(R.string.phone_label),
                    placeholder = stringResource(R.string.phone_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                TextField(
                    profile.website,
                    { onChange(profile.copy(website = it)) },
                    stringResource(R.string.website_label),
                    placeholder = stringResource(R.string.website_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            Spacer(Modifier.height(85.dp))
            Body(if (onboarding) stringResource(R.string.contacts_edit_later) else
                stringResource(R.string.contacts_shared_notice), caption = true)
            if (!onboarding) Button(stringResource(R.string.logout_account), onLogout,
                Modifier.fillMaxWidth().padding(top = 36.dp), ButtonStyle.Text)
        }
    }
}

@Preview(name = "Настройки", widthDp = 376, heightDp = 803)
@Composable
private fun EditProfileScreenPreview() {
    EditProfilePreviewContent(onboarding = false)
}

@Preview(name = "Заполнение профиля", widthDp = 376, heightDp = 803)
@Composable
private fun CompleteProfileScreenPreview() {
    EditProfilePreviewContent(onboarding = true)
}

@Composable
private fun EditProfilePreviewContent(onboarding: Boolean) {
    val initialProfile = if (onboarding) Profile(id = "preview-author", name = stringResource(R.string.preview_anna)) else ScreenPreviewData.profile
    var profile by remember {
        mutableStateOf(initialProfile)
    }
    ScreenPreview {
        EditProfileScreen(profile, onboarding, { profile = it }, {}, {}, {})
    }
}
