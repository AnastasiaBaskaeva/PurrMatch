package com.purrmatch.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.presentation.navigation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class NavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun specificListingOpensItsAuthorAndBackRestoresBothRoutes() {
        val dependencies = MockRepositories(userId = "anna")
        val matches = dependencies.matches.also { runBlocking { it.toggleFavorite("anna", "barsik").getOrThrow() } }
        val sessions = dependencies.sessions
        val restoration = StateRestorationTester(compose)
        var contactAuthor: String? = null
        restoration.setContent {
            PurrMatchApp(sessions, matches, initialDestination = Favorites, onContact = { _, author -> contactAuthor = author.id })
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Барсик, 2 года"))
        compose.onNodeWithText("Барсик, 2 года").performClick()
        compose.onNodeWithText("Барсик", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Ольга", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Мия", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Telegram", substring = false).performClick()
        compose.runOnIdle { assertEquals("olga", contactAuthor) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Ольга", substring = false).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Барсик", substring = false).assertIsDisplayed()
        pressBack()
        compose.onAllNodesWithText("Избранное", substring = false).assertCountEquals(2)
        compose.onNodeWithText("Барсик, 2 года").assertExists()
    }

    @Test fun detailsAndTabSwitchPreserveCurrentCardAndFiltersBackCancelsDraft() {
        compose.setContent { SampleApp(Screen.Home) }
        compose.onNodeWithText("Мия, 2 года").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Любая порода").performClick()
        compose.onNodeWithText("Сибирская").performClick()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Главная", substring = false).performClick()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun publishingConvertsFormValuesAndKeepsPhotosOnTheListing() {
        val dependencies = MockRepositories(userId = "anna")
        val matches = dependencies.matches
        val model = com.purrmatch.presentation.listing.NewListingViewModel(
            androidx.lifecycle.SavedStateHandle(), matches, "anna", 0)
        val form = com.purrmatch.presentation.listing.NewListingState(
            name = "Луна", breed = "Метис", birthday = "29.02.2024", paid = true, price = "5000")
        model.edit(form)
        compose.setContent {
            com.purrmatch.presentation.components.Theme {
                com.purrmatch.presentation.listing.NewListingScreen(form, model::edit, model.profile,
                    listOf(com.purrmatch.presentation.components.designUploadPhoto()), {}, {}, {},
                    { model.publish(0) {} })
            }
        }
        compose.onNodeWithText("Опубликовать анкету", substring = false).performClick()
        compose.waitUntil { matches.data.value.listing(form.id) != null }
        compose.runOnIdle {
            val listing = matches.data.value.listing(form.id)!!
            assertEquals(java.time.LocalDate.of(2024, 2, 29), listing.birthday)
            assertEquals(5000L, listing.price)
            assertEquals("anna", listing.authorId)
            assertEquals(listOf("local://placeholder/upload"), listing.photos.map { it.reference })
        }
        val sameServer = MockMatchRepository(dependencies.backend)
        assertEquals(matches.data.value.listing(form.id), runBlocking { sameServer.getListing(form.id).getOrThrow() })
        assertEquals(null, MockRepositories().matches.data.value.listing(form.id))
    }

    @Test fun editingPhoneKeepsWhatsAppFallbackOnTheCurrentAuthor() {
        val dependencies = MockRepositories(userId = "anna")
        val matches = dependencies.matches
        val sessions = dependencies.sessions
        var contactProfile: com.purrmatch.presentation.profile.Profile? = null
        compose.setContent {
            PurrMatchApp(sessions, matches, initialDestination = Settings,
                onContact = { type, author -> if (type == "whatsapp") contactProfile = author })
        }
        val phone = "+79991234567"
        compose.onNodeWithContentDescription("Телефон").performScrollTo().performTextReplacement(phone)
        compose.onNodeWithText("Сохранить изменения", substring = false).performClick()
        compose.waitUntil { matches.data.value.profile("anna")?.phone == phone }
        compose.onNodeWithText("Мия", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("WhatsApp", substring = false).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(phone, contactProfile?.phone)
            assertEquals("", contactProfile?.whatsapp)
        }
    }

    @Test fun typedBirthdayAndPriceAreFormattedInListingDetails() {
        val dependencies = MockRepositories(userId = "anna")
        val matches = dependencies.matches
        val sessions = dependencies.sessions
        val birthday = java.time.LocalDate.now().minusYears(3)
        val listing = runBlocking { matches.getListing("mia").getOrThrow() }.copy(id = "formatted-listing", birthday = birthday, price = 12_345)
        runBlocking {
            matches.publish("anna", listing).getOrThrow()
            matches.upsertProfile(matches.data.value.profile("anna")!!.copy(surname = "Иванова")).getOrThrow()
        }
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = ListingDetails(listing.id)) }
        compose.onNodeWithText("12 345 ₽").assertIsDisplayed()
        compose.onNodeWithText("Британская короткошёрстная · кошка · 3 года").assertIsDisplayed()
        val dateText = birthday.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", java.util.Locale.forLanguageTag("ru")))
        compose.onNodeWithText(dateText).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Анна Иванова", substring = false).assertIsDisplayed()
    }

    @Test fun bootstrapShowsLoadingAndFailedLoadCanBeRetried() {
        val dependencies = MockRepositories()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var loads = 0
        val matches = object : com.purrmatch.domain.match.MatchRepository by dependencies.matches {
            override suspend fun load(userId: String): Result<Unit> {
                loads++
                release.await()
                return if (loads == 1) Result.failure(IllegalStateException("offline")) else dependencies.matches.load(userId)
            }
        }
        runBlocking { dependencies.sessions.signIn("anna").getOrThrow() }
        compose.setContent { PurrMatchApp(dependencies.sessions, matches) }
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        compose.onNodeWithText("Главная", substring = false).assertDoesNotExist()
        release.complete(Unit)
        compose.onNodeWithText("Не удалось загрузить данные. Повторите попытку.").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.onNodeWithText("Главная", substring = false).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, loads) }
    }

    @Test fun missingListingHasExplicitStateInsteadOfFirstCat() {
        val dependencies = MockRepositories(userId = "olga")
        val matches = dependencies.matches
        val sessions = dependencies.sessions
        compose.setContent { PurrMatchApp(sessions, matches, initialDestination = ListingDetails("missing")) }
        compose.onAllNodesWithText("Анкета не найдена").assertCountEquals(2)
        compose.onNodeWithText("Мия", substring = false).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Мия, 2 года").assertIsDisplayed()
    }

    @Test fun logoutClearsProtectedStackAndNextUserDoesNotSeeFavorites() {
        val dependencies = MockRepositories(userId = "anna")
        val matches = dependencies.matches
        val sessions = dependencies.sessions
        compose.setContent { PurrMatchApp(sessions, matches) }
        compose.onNodeWithText("Профиль", substring = false).performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.onNodeWithText("Выйти", substring = false).performClick()
        compose.onNodeWithText("Войти", substring = false).assertIsDisplayed()
        // A new session gets new tab stacks and entry-scoped ViewModels.
        compose.onNodeWithContentDescription("Логин").performTextInput("olga_cats")
        compose.onNodeWithContentDescription("Пароль", useUnmergedTree = true).performTextInput("purrmatch-demo")
        compose.onNodeWithText("Войти", substring = false).performClick()
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Главная", substring = false).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.path
            java.io.File(output, "account-switch.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("Избранное", substring = false).performClick()
        compose.onNodeWithText("Пока пусто").assertExists()
    }

    @Test fun reopeningListingKeepsIntermediateAuthorInBackHistory() {
        val dependencies = MockRepositories(userId = "olga")
        compose.setContent { PurrMatchApp(dependencies.sessions, dependencies.matches) }
        compose.onNodeWithText("Мия, 2 года").performClick()
        compose.onNodeWithText("Профиль автора и контакты").performClick()
        compose.onNodeWithText("Мия", substring = false).performClick()
        pressBack()
        compose.onNodeWithText("Анна", substring = false).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Мия", substring = false).assertIsDisplayed()
    }
}
