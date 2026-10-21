package com.purrmatch.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.stringResource
import com.purrmatch.presentation.R
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.purrmatch.presentation.components.preview.ComponentsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun textEntryAndPasswordVisibility() {
        var entered = ""
        var visible = false
        compose.setContent {
            var login by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var reveal by remember { mutableStateOf(false) }
            Theme {
                androidx.compose.foundation.layout.Column {
                    TextField(login, { login = it; entered = it }, "Логин")
                    PasswordField(password, { password = it }, "Пароль", reveal, { reveal = it; visible = it })
                }
            }
        }
        compose.onNodeWithContentDescription("Логин").performTextInput("anna_cats")
        compose.onNodeWithContentDescription("Пароль").performTextInput("secret123")
        compose.runOnIdle { assertEquals("anna_cats", entered) }
        compose.onNodeWithContentDescription("Показать пароль").performClick()
        compose.runOnIdle { assertTrue(visible) }
        compose.onNodeWithContentDescription("Скрыть пароль").performClick()
        compose.runOnIdle { assertFalse(visible) }
    }

    @Test fun selectionAndSwitchAreControlledByCaller() {
        var checked = true
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            var enabled by remember { mutableStateOf(true) }
            Theme {
                androidx.compose.foundation.layout.Column {
                    ChipGroup("Пол", listOf("Любой", "Кошка", "Кот"), selected, { selected = it })
                    SwitchRow("С собаками", enabled, { enabled = it; checked = it })
                }
            }
        }
        compose.onNodeWithText("Кошка").performClick().assertIsSelected()
        compose.onNodeWithText("С собаками").performClick()
        compose.runOnIdle { assertFalse(checked) }
    }

    @Test fun trailingActionDoesNotOpenCard() {
        var opened = 0
        var deleted = 0
        compose.setContent {
            Theme {
                CatListItem("Мия", "Бесплатно", { opened++ }) {
                    IconButton(Icon.Delete, "Удалить анкету", { deleted++ })
                }
            }
        }
        compose.onNodeWithContentDescription("Удалить анкету").performClick()
        compose.runOnIdle { assertEquals(1, deleted); assertEquals(0, opened) }
        compose.onNodeWithText("Мия").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun disabledButtonDoesNotInvokeAction() {
        var clicked = false
        compose.setContent {
            Theme { Button("Войти", { clicked = true }, enabled = false) }
        }
        compose.onNodeWithText("Войти").performClick()
        compose.runOnIdle { assertFalse(clicked) }
    }

    @Test fun swipeEmitsOnceAndSmallDragCancels() {
        var skipped = 0
        var liked = 0
        compose.setContent {
            Theme {
                SwipeCard({ liked++ }, { skipped++ }, Modifier.requiredSize(300.dp, 400.dp).testTag("swipe")) {
                    Text("Мия")
                }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput {
            swipe(Offset(width * .5f, height * .5f), Offset(width * .55f, height * .5f), 300)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, skipped); assertEquals(0, liked) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, skipped); assertEquals(0, liked) }
    }

    @Test fun confirmationRequiresExplicitAction() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            Theme {
                ConfirmationDialog(Confirmation.DeleteListing,
                    { confirmed = true }, { dismissed = true })
            }
        }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
        compose.onNodeWithText("Удалить", substring = false).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test fun dropdownReportsSelection() {
        var selected = ""
        compose.setContent {
            var value by remember { mutableStateOf("Выберите город") }
            Theme {
                DropdownField("Город", value, listOf("Москва", "Казань"), { value = it; selected = it })
            }
        }
        compose.onNodeWithText("Выберите город").performClick()
        compose.onNodeWithText("Казань").performClick()
        compose.runOnIdle { assertEquals("Казань", selected) }
    }

    @Test fun photoPickerStopsAtFourAndAllowsRemoval() {
        val photo = ColorPainter(Color.Gray)
        var count = 1
        compose.setContent {
            var photos by remember { mutableStateOf(listOf(photo)) }
            Theme {
                PhotoPicker(photos, { photos = photos + photo; count = photos.size },
                    { index -> photos = photos.filterIndexed { i, _ -> i != index }; count = photos.size })
            }
        }
        repeat(3) { compose.onNodeWithContentDescription("Добавить фотографию").performClick() }
        compose.onNodeWithContentDescription("Добавить фотографию").assertDoesNotExist()
        compose.runOnIdle { assertEquals(4, count) }
        compose.onNodeWithContentDescription("Фотография 2").performClick()
        compose.runOnIdle { assertEquals(3, count) }
    }

    @Test fun gallerySwipesBetweenPhotos() {
        var currentPage = 0
        compose.setContent {
            val state = rememberPagerState { 3 }
            currentPage = state.currentPage
            Theme {
                PhotoGallery(listOf(ColorPainter(Color.Red), ColorPainter(Color.Green), ColorPainter(Color.Blue)),
                    Modifier.requiredSize(300.dp, 300.dp).testTag("gallery"), state)
            }
        }
        compose.onNodeWithTag("gallery").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, currentPage) }
    }

    @Test fun renderSamplesAtFigmaDimensions() {
        var sample by mutableStateOf("home")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(376.dp, 803.dp).testTag("sample")) {
                    androidx.compose.runtime.key(sample) {
                        if (sample == "delete-card" || sample == "logout-card") {
                            val deletion = sample == "delete-card"
                            Theme {
                                Background(Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize().background(Colors.Scrim))
                                    ConfirmationCard(
                                        stringResource(if (deletion) R.string.delete_title else R.string.logout_title),
                                        stringResource(if (deletion) R.string.delete_message else R.string.logout_message),
                                        stringResource(if (deletion) R.string.delete else R.string.logout), {}, {},
                                        Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                                        icon = if (deletion) Icon.Delete else Icon.Logout,
                                    )
                                }
                            }
                        } else ComponentsCatalog(sample)
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "component-screenshots")
            else File(context.getExternalFilesDir(null), "component-screenshots")
        directory.mkdirs()
        for (name in listOf("home", "filters", "login", "buttons", "fields", "cards", "profile", "photos", "empty", "notices", "swipe-right", "swipe-left", "delete-card", "logout-card")) {
            compose.runOnIdle { sample = name }
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("sample").captureToImage().asAndroidBitmap()
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
