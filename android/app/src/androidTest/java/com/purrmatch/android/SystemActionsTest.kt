package com.purrmatch.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SystemActionsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun selectedPhotoIsDisplayedAndReloadedAfterRecreationAndPickerCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val photo = File(context.cacheDir, "picked-photo-test.png")
        val bitmap = Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var pickerResult = Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(photo))
        )
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action in listOf(
                        "android.provider.action.PICK_IMAGES",
                        "androidx.activity.result.contract.action.PICK_IMAGES",
                        Intent.ACTION_OPEN_DOCUMENT,
                        Intent.ACTION_GET_CONTENT,
                    )) pickerResult else null
        }
        instrumentation.addMonitor(monitor)
        try {
            val intent = Intent(context, MainActivity::class.java).putExtra("screen", "new-listing")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.onNodeWithContentDescription("Добавить фотографию").performClick()
                assertPickedPhoto()
                scenario.recreate()
                assertPickedPhoto()
                pickerResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                compose.onNodeWithContentDescription("Добавить фотографию").performClick()
                assertPickedPhoto()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            photo.delete()
        }
    }

    @Test
    fun authorContactOpensPreparedTelegramAddress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var opened: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                opened = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val intent = Intent(instrumentation.targetContext, MainActivity::class.java).putExtra("screen", "author")
            ActivityScenario.launch<MainActivity>(intent).use {
                compose.onNodeWithText("Telegram", substring = false).performClick()
                compose.runOnIdle {
                    assertEquals(Intent.ACTION_VIEW, opened?.action)
                    assertEquals("https://t.me/anna_cats", opened?.dataString)
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun assertPickedPhoto() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Фотография 2").fetchSemanticsNodes().isNotEmpty()
        }
        val image = compose.onNodeWithContentDescription("Фотография 2").assertIsDisplayed()
            .captureToImage().asAndroidBitmap()
        assertEquals(Color.GREEN, image.getPixel(image.width / 2, image.height / 2))
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.path
        File(output, "picked-photo.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
