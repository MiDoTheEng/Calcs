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
class AndroidPainter : Painter {
    override val pageW = 595f
    override val pageH = 842f
    val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var n = 0
    private val reg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun newPage() {
        n++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), n).create())
    }
    override fun endPage() { page?.let { doc.finishPage(it) }; page = null }
    private fun p(size: Float, b: Boolean) = (if (b) bold else reg).also { it.textSize = size }
    override fun measure(s: String, size: Float, bold: Boolean) = p(size, bold).measureText(s)
    override fun text(x: Float, y: Float, s: String, size: Float, bold: Boolean, color: Int, align: Int) {
        val c = page?.canvas ?: return
        val paint = p(size, bold); paint.color = color
        val w = paint.measureText(s)
        val xx = when (align) { 2 -> x - w; 1 -> x - w / 2; else -> x }
        c.drawText(s, xx, y + size * 0.8f, paint)
    }
    override fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int?, lw: Float) {
        val c = page?.canvas ?: return
        if (fill != null) { this.fill.color = fill; c.drawRect(x, y, x + w, y + h, this.fill) }
        if (stroke != null) { this.stroke.color = stroke; this.stroke.strokeWidth = lw; c.drawRect(x, y, x + w, y + h, this.stroke) }
    }
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, lw: Float) {
        val c = page?.canvas ?: return
        stroke.color = color; stroke.strokeWidth = lw; c.drawLine(x1, y1, x2, y2, stroke)
    }
}

class Saved(val uri: Uri, val mime: String, val name: String)

object Exporter {
    fun build(app: AppState, branches: List<Branch>, pdf: Boolean): Pair<String, ByteArray> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val model = Reports.build(branches, app.states, app.project, today)
        val title = if (branches.size > 1) "MEP Calculations" else branches[0].title
        val slug = { s: String -> s.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF]+"), "_").trim('_').take(40) }
        val name = listOf("Calcs", slug(app.project.name).ifEmpty { "Project" }, slug(title), today).joinToString("_") + if (pdf) ".pdf" else ".xlsx"
        val bytes = if (pdf) {
            val painter = AndroidPainter()
            val accent = if (branches.size > 1) 0xFF27364A.toInt() else branches[0].accent.toInt()
            PdfLayout.render(painter, model, title, accent)
            val out = ByteArrayOutputStream(); painter.doc.writeTo(out); painter.doc.close(); out.toByteArray()
        } else Xlsx.render(model)
        return name to bytes
    }

    fun save(ctx: Context, name: String, bytes: ByteArray): Saved {
        val mime = if (name.endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val cr = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Calcs")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Could not create the file")
        cr.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file")
        cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return Saved(uri, mime, name)
    }

    fun open(ctx: Context, s: Saved) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, s.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app installed that opens this file. It is in Downloads/Calcs.", Toast.LENGTH_LONG).show() }
    }

    fun share(ctx: Context, s: Saved) {
        val i = Intent(Intent.ACTION_SEND).setType(s.mime).putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Share calculation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app available to share with.", Toast.LENGTH_LONG).show() }
    }
}
