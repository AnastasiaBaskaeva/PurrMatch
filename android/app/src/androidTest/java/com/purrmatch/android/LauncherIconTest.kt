package com.purrmatch.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LauncherIconTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun launcherResolvesMascotAndOpensApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotEquals(0, info.icon)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
        val icon = context.packageManager.getApplicationIcon(info)
        if (Build.VERSION.SDK_INT >= 26) {
            assertTrue(icon is AdaptiveIconDrawable)
            icon as AdaptiveIconDrawable
            if (Build.VERSION.SDK_INT >= 33) assertNotNull(icon.monochrome)
            // Android reserves the central 66dp circle of the 108dp foreground for all masks.
            val foreground = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
            icon.foreground.setBounds(0, 0, 108, 108)
            icon.foreground.draw(Canvas(foreground))
            for (y in 0 until 108) for (x in 0 until 108) {
                if (Color.alpha(foreground.getPixel(x, y)) > 0) {
                    assertTrue("Mascot outside safe zone at $x,$y", (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 33 * 33)
                }
            }
        }
        // Keep renderings for visual inspection of common launcher masks.
        for (round in listOf(false, true)) {
            val preview = context.packageManager.getApplicationIcon(info).mutate()
            val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val mask = Path().apply {
                if (round) addCircle(216f, 216f, 216f, Path.Direction.CW)
                else addRoundRect(RectF(0f, 0f, 432f, 432f), 96f, 96f, Path.Direction.CW)
            }
            canvas.clipPath(mask)
            if (Build.VERSION.SDK_INT >= 26 && preview is AdaptiveIconDrawable) {
                preview.background.setBounds(0, 0, 432, 432)
                preview.background.draw(canvas)
                preview.foreground.setBounds(-108, -108, 540, 540)
                preview.foreground.draw(canvas)
            } else {
                preview.setBounds(0, 0, 432, 432)
                preview.draw(canvas)
            }
            File(context.getExternalFilesDir(null), "launcher-${if (round) "circle" else "rounded"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ActivityScenario.launch<MainActivity>(launchIntent).use {
            compose.waitUntil(10_000) {
                runCatching { compose.onRoot().fetchSemanticsNode() }.isSuccess
            }
            compose.onRoot().assertIsDisplayed()
        }
    }
}
