package com.calcs.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shot() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        File(ctx.filesDir, "calcs.json").delete()
        val app = AppState(ctx)
        app.screen = when (screen) {
            "home" -> Screen.Home
            "units" -> Screen.Units
            else -> Screen.BranchScr(screen)
        }
        rule.setContent { CalcsApp(app) }
        rule.waitForIdle()
        if (tab > 0) {   // tab index 0 = overview, tools start at 1
            val b = Catalog.BRANCHES.first { it.id == screen }
            rule.onNodeWithText(b.tools[tab - 1].title, useUnmergedTree = true).performClick()
            rule.waitForIdle()
        }
        if (name.startsWith("keypad")) {   // open the number pad on "Number of identical units"
            rule.onAllNodesWithText("20", useUnmergedTree = true)[0].performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Next ›", useUnmergedTree = true).performClick()
            rule.waitForIdle()
            rule.onNodeWithText("7", useUnmergedTree = true).performClick()
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
        }
        if (name.startsWith("rooms")) {   // Clear → add a bathroom and a kitchen → totals must follow
            rule.onAllNodesWithText("Clear", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
            rule.onAllNodesWithText("Clear", useUnmergedTree = true).let { it[it.fetchSemanticsNodes().size - 1] }.performClick(); rule.waitForIdle()
            rule.onAllNodesWithText("Bathroom", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
            rule.onAllNodesWithText("Kitchen", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
            rule.onAllNodesWithText("+", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
            rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        }
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
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
            l.add(arrayOf("01_home_phone_dark", "home", 0, "w411dp-h891dp-night-mdpi"))
            Catalog.BRANCHES.forEach { b ->
                l.add(arrayOf("map_${b.id}_tablet", b.id, 0, TAB_L))
                b.tools.forEachIndexed { i, t -> l.add(arrayOf("tab_${b.id}_${i}_${t.id}", b.id, i + 1, TAB_TALL)) }
            }
            l.add(arrayOf("map_hvac_phone", "hvac", 0, PHONE_TALL))
            l.add(arrayOf("map_fire_tabP_dark", "fire", 0, "w800dp-h1280dp-night-mdpi"))
            l.add(arrayOf("tabP_water_0", "water", 1, TAB_P_TALL))
            l.add(arrayOf("tabP_hvac_0", "hvac", 1, "w800dp-h2600dp-night-mdpi"))
            l.add(arrayOf("phone_water_0", "water", 1, PHONE_TALL))
            l.add(arrayOf("phone_drainage_1", "drainage", 2, PHONE_TALL))
            l.add(arrayOf("phone_fire_2_dark", "fire", 3, "w411dp-h2600dp-night-mdpi"))
            l.add(arrayOf("phone_hvac_0", "hvac", 1, PHONE_TALL))
            l.add(arrayOf("phone_hvac_2", "hvac", 3, PHONE_TALL))
            l.add(arrayOf("phone_fire_0_view", "fire", 1, PHONE))
            l.add(arrayOf("units_tablet", "units", 0, "w800dp-h1280dp-mdpi"))
            l.add(arrayOf("keypad_tablet", "water", 1, TAB_L))
            l.add(arrayOf("keypad_phone", "water", 1, PHONE))
            l.add(arrayOf("rooms_flow", "water", 1, TAB_TALL))
            return l
        }
    }
}
