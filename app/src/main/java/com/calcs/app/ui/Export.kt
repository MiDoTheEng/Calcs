package com.calcs.app.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.calcs.app.core.Branch
import com.calcs.app.core.PdfLayout
import com.calcs.app.core.Painter
import com.calcs.app.core.Reports
import com.calcs.app.core.Xlsx
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draws the report onto Android's PdfDocument (A4, units = points). */
open class AndroidPainter(private val ctx: Context) : Painter {
    private val pdfDensity = androidx.compose.ui.unit.Density(0.75f)
    private val tm by lazy {
        androidx.compose.ui.text.TextMeasurer(androidx.compose.ui.text.font.createFontFamilyResolver(ctx), pdfDensity, androidx.compose.ui.unit.LayoutDirection.Ltr)
    }
    override val pageW = 595f
    override val pageH = 842f
    val doc by lazy { PdfDocument() }
    private var page: PdfDocument.Page? = null
    /** Canvas of the current page (overridable so the layout can be rendered to images in tests). */
    open fun canvas(): android.graphics.Canvas? = page?.canvas
    private var n = 0
    private val reg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun newPage() = startPdfPage()
    protected fun startPdfPage() {
        n++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), n).create())
    }
    override fun endPage() { page?.let { doc.finishPage(it) }; page = null }
    private fun p(size: Float, b: Boolean) = (if (b) bold else reg).also { it.textSize = size }
    override fun measure(s: String, size: Float, bold: Boolean) = p(size, bold).measureText(s)
    override fun text(x: Float, y: Float, s: String, size: Float, bold: Boolean, color: Int, align: Int) {
        val c = canvas() ?: return
        val paint = p(size, bold); paint.color = color
        val w = paint.measureText(s)
        val xx = when (align) { 2 -> x - w; 1 -> x - w / 2; else -> x }
        c.drawText(s, xx, y + size * 0.8f, paint)
    }
    override fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int?, lw: Float) {
        val c = canvas() ?: return
        if (fill != null) { this.fill.color = fill; c.drawRect(x, y, x + w, y + h, this.fill) }
        if (stroke != null) { this.stroke.color = stroke; this.stroke.strokeWidth = lw; c.drawRect(x, y, x + w, y + h, this.stroke) }
    }
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, lw: Float) {
        val c = canvas() ?: return
        stroke.color = color; stroke.strokeWidth = lw; c.drawLine(x1, y1, x2, y2, stroke)
    }

    override fun schematicHeight(branchId: String, w: Float): Float = schematicFor(branchId)?.let { w * it.h / it.w + 20f } ?: 0f

    override fun schematic(branchId: String, x: Float, y: Float, w: Float): Float {
        val sch = schematicFor(branchId) ?: return 0f
        val c = canvas() ?: return 0f
        val h = w * sch.h / sch.w
        val accent = androidx.compose.ui.graphics.Color(com.calcs.app.core.Catalog.BRANCHES.firstOrNull { it.id == branchId }?.accent ?: 0xFF27364A)
        // Draw the schematic into a high-resolution bitmap, then place the bitmap on the page.
        // (Same path as the on-screen drawing; works on every PDF canvas.)
        val k3 = 4f
        val bmp = android.graphics.Bitmap.createBitmap((w * k3).toInt(), (h * k3).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
        val bc = android.graphics.Canvas(bmp)
        bc.drawColor(0xFFFBFCFE.toInt())
        bc.scale(k3, k3)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(pdfDensity, androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bc), androidx.compose.ui.geometry.Size(w, h)) {
            val k = Sk(this, w / sch.w, tm, LightColors, accent, -1)
            sch.draw(k)
            sch.comps.forEach { k.badge(it) }
        }
        c.drawBitmap(bmp, null, android.graphics.RectF(x, y, x + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        rect(x, y, w, h, null, 0xFFCFD6DF.toInt(), 0.6f)
        // legend
        var lx = x; val ly = y + h + 9f
        sch.legend.forEach { l ->
            stroke.color = l.color.toArgbInt(); stroke.strokeWidth = 2.2f
            stroke.pathEffect = if (l.dashed) android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) else null
            c.drawLine(lx, ly, lx + 16f, ly, stroke); stroke.pathEffect = null
            text(lx + 20f, ly - 3.5f, l.label, 7f, false, 0xFF1B2430.toInt())
            lx += 26f + measure(l.label, 7f, false) + 10f
        }
        return h + 20f
    }

    override fun schematicParts(branchId: String): List<String> {
        val sch = schematicFor(branchId) ?: return emptyList()
        val br = com.calcs.app.core.Catalog.BRANCHES.firstOrNull { it.id == branchId }
        return sch.comps.map { c -> "${c.n}.  ${c.name}" + (c.tool?.let { t -> br?.tools?.getOrNull(t)?.let { "  (sized in: ${it.title})" } } ?: "") }
    }
}

class Saved(val uri: Uri, val mime: String, val name: String, val folder: String)

object Exporter {
    fun build(ctx: Context, app: AppState, branches: List<Branch>, pdf: Boolean): Pair<String, ByteArray> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val model = Reports.build(branches, app.states, app.project, today)
        val full = com.calcs.app.core.Catalog.BRANCHES.firstOrNull { it.id == branches[0].id }
        val title = when {
            branches.size > 1 -> "MEP Calculations"
            branches[0].tools.size == 1 && (full?.tools?.size ?: 1) > 1 -> "${branches[0].title} – ${branches[0].tools[0].title}"
            else -> branches[0].title
        }
        val slug = { s: String -> s.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF]+"), "_").trim('_').take(40) }
        val name = listOf("Calcs", slug(app.project.name).ifEmpty { "Project" }, slug(title), today).joinToString("_") + if (pdf) ".pdf" else ".xlsx"
        val bytes = if (pdf) {
            val painter = AndroidPainter(ctx)
            val accent = if (branches.size > 1) 0xFF27364A.toInt() else branches[0].accent.toInt()
            PdfLayout.render(painter, model, title, accent)
            val out = ByteArrayOutputStream(); painter.doc.writeTo(out); painter.doc.close(); out.toByteArray()
        } else Xlsx.render(model)
        return name to bytes
    }

    /** Folder for a project: Downloads/Calcs/<project name> (characters Android doesn't allow removed). */
    fun projectFolder(project: String): String =
        project.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().trim('.').take(60).ifEmpty { "Untitled project" }

    fun save(ctx: Context, name: String, bytes: ByteArray, project: String): Saved {
        val folder = "Calcs/" + projectFolder(project)
        val mime = if (name.endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val cr = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Could not create the file")
        cr.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file")
        cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return Saved(uri, mime, name, "Downloads/$folder")
    }

    fun open(ctx: Context, s: Saved) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, s.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app installed that opens this file. It is in ${s.folder}.", Toast.LENGTH_LONG).show() }
    }

    fun shareMany(ctx: Context, list: List<Saved>) {
        if (list.size == 1) { share(ctx, list[0]); return }
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list.map { it.uri })).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Share calculations").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app available to share with.", Toast.LENGTH_LONG).show() }
    }

    fun share(ctx: Context, s: Saved) {
        val i = Intent(Intent.ACTION_SEND).setType(s.mime).putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Share calculation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app available to share with.", Toast.LENGTH_LONG).show() }
    }
}
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
