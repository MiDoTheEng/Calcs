package com.calcs.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.calcs.app.core.Catalog
import com.calcs.app.core.PdfLayout
import com.calcs.app.core.Project
import com.calcs.app.core.Reports
import com.calcs.app.ui.AndroidPainter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders the PDF report layout (same painter code as the app) into PNG pages for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfRenderTest {
    class BitmapPainter(ctx: Context, private val prefix: String) : AndroidPainter(ctx) {
        private var bmp: Bitmap? = null; private var cv: Canvas? = null; var pages = 0
        override fun canvas(): Canvas? = cv
        override fun newPage() {
            bmp = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
            cv = Canvas(bmp!!).apply { drawColor(android.graphics.Color.WHITE); scale(2f, 2f) }
        }
        override fun endPage() {
            val b = bmp ?: return
            pages++
            val dir = File("build/shots"); dir.mkdirs()
            FileOutputStream(File(dir, "${prefix}-p$pages.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp = null; cv = null
        }
    }

    /** The real export path (PdfDocument), written to a file so the PDF itself can be inspected. */
    @Test
    fun realPdf() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val app = com.calcs.app.ui.AppState(ctx)
        val dir = File("build/shots"); dir.mkdirs()
        listOf("water", "hvac").forEach { id ->
            val b = Catalog.BRANCHES.first { it.id == id }
            val (_, bytes) = com.calcs.app.ui.Exporter.build(ctx, app, listOf(b), true)
            File(dir, "real_$id.pdf").writeBytes(bytes)
        }
    }

    @Test
    fun renderReports() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val states = Catalog.TOOLS.associate { it.id to it.defaults() }
        val proj = Project("Al Reem Tower", "Abu Dhabi", "Eng. Ahmed Taha", "", "")
        Catalog.BRANCHES.forEach { b ->
            val m = Reports.build(listOf(b), states, proj, "2026-11-23")
            val p = BitmapPainter(ctx, "pdf_${b.id}")
            PdfLayout.render(p, m, b.title, b.accent.toInt())
        }
    }
}
