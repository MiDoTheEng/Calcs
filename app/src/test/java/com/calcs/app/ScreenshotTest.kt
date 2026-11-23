package com.calcs.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.calcs.app.core.Catalog
import com.calcs.app.ui.AppState
import com.calcs.app.ui.CalcsApp
import com.calcs.app.ui.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders every screen at phone and tablet sizes so the layouts can be reviewed as images. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest(private val name: String, private val screen: String, private val tab: Int, private val qualifiers: String) {

    @get:Rule val rule = createComposeRule()

    @Test
    fun shot() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        File(ctx.filesDir, "calcs.json").delete()
        val app = AppState(ctx)
        app.screen = when (screen) {
            "home" -> Screen.Home
            "units" -> Screen.Units
            else -> { app.lastTab[screen] = tab; Screen.BranchScr(screen) }
        }
        rule.setContent { CalcsApp(app) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots"); dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val TAB_L = "w1280dp-h800dp-mdpi"
        private const val TAB_TALL = "w1280dp-h2600dp-mdpi"
        private const val TAB_P_TALL = "w800dp-h2600dp-mdpi"
        private const val PHONE = "w411dp-h891dp-mdpi"
        private const val PHONE_TALL = "w411dp-h2600dp-mdpi"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> {
            val l = mutableListOf<Array<Any>>()
            l.add(arrayOf("00_home_tablet", "home", 0, TAB_L))
            l.add(arrayOf("01_home_phone_dark", "home", 0, "$PHONE-night"))
            Catalog.BRANCHES.forEach { b -> b.tools.forEachIndexed { i, t -> l.add(arrayOf("tab_${b.id}_${i}_${t.id}", b.id, i, TAB_TALL)) } }
            l.add(arrayOf("tabP_water_0", "water", 0, TAB_P_TALL))
            l.add(arrayOf("tabP_hvac_0", "hvac", 0, "$TAB_P_TALL-night"))
            l.add(arrayOf("phone_water_0", "water", 0, PHONE_TALL))
            l.add(arrayOf("phone_drainage_1", "drainage", 1, PHONE_TALL))
            l.add(arrayOf("phone_fire_2_dark", "fire", 2, "$PHONE_TALL-night"))
            l.add(arrayOf("phone_hvac_0", "hvac", 0, PHONE_TALL))
            l.add(arrayOf("phone_hvac_2", "hvac", 2, PHONE_TALL))
            l.add(arrayOf("phone_fire_0_view", "fire", 0, PHONE))
            l.add(arrayOf("units_tablet", "units", 0, "w800dp-h1280dp-mdpi"))
            return l
        }
    }
}
