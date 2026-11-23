package com.calcs.app.core

/* PDF report layout, independent of the drawing backend (Android PdfDocument on device). */

interface Painter {
    val pageW: Float
    val pageH: Float
    fun newPage()
    fun endPage()
    /** y = top of the text line box */
    fun text(x: Float, y: Float, s: String, size: Float, bold: Boolean, color: Int, align: Int = 0)
    fun measure(s: String, size: Float, bold: Boolean): Float
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int? = null, lw: Float = 0.5f)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, lw: Float = 0.5f)
    /** System schematic (drawn by the app). Height it needs at width w, 0 if none. */
    fun schematicHeight(branchId: String, w: Float): Float = 0f
    /** Draws the schematic + legend at (x, y), returns the height used. */
    fun schematic(branchId: String, x: Float, y: Float, w: Float): Float = 0f
    /** Numbered component lines for the schematic. */
    fun schematicParts(branchId: String): List<String> = emptyList()
}

object PdfLayout {
    private const val INK = 0xFF1B2430.toInt()
    private const val MUTED = 0xFF5D6B7D.toInt()
    private const val RULE = 0xFFCFD6DF.toInt()
    private const val INFILL = 0xFFFFF6D6.toInt()
    private const val KEYFILL = 0xFFDFF1E2.toInt()
    private const val OKC = 0xFF1D7A3A.toInt()
    private const val BADC = 0xFFB3261E.toInt()
    private const val ZEBRA = 0xFFF4F6F9.toInt()
    private const val HEAD = 0xFF27364A.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun wrap(p: Painter, s: String, size: Float, bold: Boolean, maxW: Float): List<String> {
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>(); var cur = ""
        for (w0 in words) {
            var w = w0
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (p.measure(t, size, bold) <= maxW) { cur = t; continue }
            if (cur.isNotEmpty()) lines.add(cur)
            while (p.measure(w, size, bold) > maxW && w.length > 1) {
                var k = w.length
                while (k > 1 && p.measure(w.substring(0, k), size, bold) > maxW) k--
                lines.add(w.substring(0, k)); w = w.substring(k)
            }
            cur = w
        }
        if (cur.isNotEmpty() || lines.isEmpty()) lines.add(cur)
        return lines
    }

    fun render(p: Painter, m: ReportModel, title: String, accent: Int) {
        val mg = 40f; val top = 64f; val bottom = p.pageH - 46f; val avail = p.pageW - 2 * mg
        var y = 0f
        var pageNo = 0
        val proj = m.project
        val heading = title.uppercase()

        fun header() {
            pageNo++
            p.rect(0f, 0f, p.pageW, 6f, accent)
            p.text(mg, 18f, "CALCS  ·  $heading", 8f, true, accent)
            p.text(p.pageW - mg, 18f, proj.name.ifBlank { "Untitled project" }, 8f, true, INK, 2)
            p.text(p.pageW - mg, 29f, proj.date.ifBlank { m.date }, 7f, false, MUTED, 2)
            p.line(mg, 43f, p.pageW - mg, 43f, RULE, 0.6f)
            p.line(mg, p.pageH - 34f, p.pageW - mg, p.pageH - 34f, RULE, 0.6f)
            p.text(mg, p.pageH - 29f, "Educational tool – verify every value against the governing code edition and authority requirements.", 6.8f, false, MUTED)
            p.text(p.pageW - mg, p.pageH - 29f, "Page $pageNo", 7f, true, MUTED, 2)
        }
        fun newPage() { if (pageNo > 0) p.endPage(); p.newPage(); header(); y = top }
        fun ensure(h: Float) { if (y + h > bottom) newPage() }

        newPage()
        p.text(mg, y, title, 20f, true, INK); y += 28f
        p.text(mg, y, "Calculation report", 10f, false, MUTED); y += 22f
        val info = listOf("Project" to proj.name, "Location" to proj.location, "Prepared by" to proj.engineer, "Client / reference" to proj.client, "Date" to proj.date.ifBlank { m.date })
            .filter { it.second.isNotBlank() }
        info.forEachIndexed { i, r ->
            if (i % 2 == 0) p.rect(mg, y, avail, 16f, ZEBRA)
            p.text(mg + 6, y + 4, r.first, 8.5f, true, MUTED); p.text(mg + 130, y + 4, r.second, 8.5f, false, INK); y += 16f
        }
        y += 8f
        val tools = m.branches.flatMap { b -> b.tools.map { b to it } }
        val multi = m.branches.size > 1
        p.text(mg, y, "Contents", 9f, true, INK); y += 14f
        tools.forEachIndexed { i, (b, t) -> ensure(12f); p.text(mg + 6, y, "${i + 1}.  " + (if (multi) "${b.title} – " else "") + t.title, 8.5f, false, INK); y += 12f }
        y += 10f

        fun blockTitle(s: String) { ensure(34f); p.text(mg, y, s, 9.5f, true, accent); y += 15f }

        fun note(s: String) {
            val lines = wrap(p, s, 7.8f, false, avail - 16)
            val h = lines.size * 10f + 8f
            ensure(h)
            p.rect(mg, y, 2f, h, RULE)
            lines.forEachIndexed { k, l -> p.text(mg + 9, y + 4 + k * 10f, l, 7.8f, false, MUTED) }
            y += h + 10f
        }

        fun kv(b: RKv) {
            if (b.rows.isEmpty()) return
            blockTitle(b.title)
            val cw = floatArrayOf(avail * 0.58f, avail * 0.26f, avail * 0.16f); val sz = 8.5f
            b.rows.forEachIndexed { i, r ->
                val bold = r.kind == CK.KEY
                val ll = wrap(p, r.label, sz, bold, cw[0] - 12)
                val vl = wrap(p, r.cell.text, sz, bold, cw[1] - 10)
                val h = maxOf(ll.size, vl.size) * 11f + 6f
                ensure(h)
                if (i % 2 == 0) p.rect(mg, y, avail, h, ZEBRA)
                if (r.kind == CK.IN) p.rect(mg + cw[0], y, cw[1], h, INFILL)
                if (r.kind == CK.KEY) p.rect(mg + cw[0], y, cw[1], h, KEYFILL)
                ll.forEachIndexed { k, l -> p.text(mg + 6, y + 4 + k * 11f, l, sz, bold, INK) }
                val c = when (r.cell.status) { Status.OK -> OKC; Status.BAD -> BADC; else -> INK }
                vl.forEachIndexed { k, l -> p.text(mg + cw[0] + cw[1] - 6, y + 4 + k * 11f, l, sz, r.kind != CK.IN, c, 2) }
                p.text(mg + cw[0] + cw[1] + 6, y + 4, r.unit, 8f, false, MUTED)
                y += h
            }
            p.line(mg, y, mg + avail, y, RULE, 0.6f)
            y += 12f
        }

        fun table(b: RTable) {
            blockTitle(b.title)
            if (b.rows.isEmpty()) { note("No rows entered."); return }
            val n = b.head.size
            val sz = if (n > 10) 6.4f else if (n > 7) 7.0f else 7.8f
            val hs = sz - 0.3f; val pad = 3.5f
            val nat = FloatArray(n); val minw = FloatArray(n)
            for (j in 0 until n) {
                var hw = 0f
                b.head[j].split(" ").forEach { hw = maxOf(hw, p.measure(it, hs, true)) }
                var cw = p.measure(b.head[j], hs, true)
                var ww = hw
                (b.rows + listOfNotNull(b.totals)).forEach { row ->
                    val bold = b.kinds[j] == CK.KEY
                    cw = maxOf(cw, p.measure(row[j].text, sz, bold))
                    row[j].text.split(" ").forEach { ww = maxOf(ww, p.measure(it, sz, true)) }
                }
                nat[j] = cw + 2 * pad; minw[j] = (ww + 2 * pad).coerceIn(24f, 110f)
            }
            var tot = nat.sum(); val w = nat.copyOf()
            if (tot > avail) {
                var guard = 0
                while (tot > avail + 0.1f && guard++ < 600) {
                    var big = -1
                    for (k in 0 until n) if (w[k] > minw[k] + 0.5f && (big < 0 || w[k] > w[big])) big = k
                    if (big < 0) break
                    val cut = minOf(w[big] - minw[big], tot - avail, 4f)
                    w[big] -= cut; tot -= cut
                }
                if (tot > avail) for (k in 0 until n) w[k] = w[k] * avail / tot
            } else for (k in 0 until n) w[k] = w[k] * avail / tot

            fun headRow() {
                val hl = b.head.mapIndexed { j, h -> wrap(p, h, hs, true, w[j] - 2 * pad) }
                val hh = hl.maxOf { it.size } * (hs + 2) + 7
                ensure(hh + 14)
                p.rect(mg, y, avail, hh, HEAD)
                var x = mg
                hl.forEachIndexed { j, lines -> lines.forEachIndexed { k, l -> p.text(x + pad, y + 4 + k * (hs + 2), l, hs, true, WHITE) }; x += w[j] }
                y += hh
            }
            headRow()
            fun drawRow(row: List<Cell>, i: Int, total: Boolean) {
                val lines = row.mapIndexed { j, c -> wrap(p, c.text, sz, b.kinds[j] == CK.KEY || total, w[j] - 2 * pad) }
                val h = lines.maxOf { it.size } * (sz + 2.5f) + 5
                if (y + h > bottom) { newPage(); headRow() }
                if (total) p.rect(mg, y, avail, h, 0xFFE6EBF1.toInt()) else if (i % 2 == 1) p.rect(mg, y, avail, h, ZEBRA)
                var x = mg
                row.forEachIndexed { j, c ->
                    if (!total && b.kinds[j] == CK.IN) p.rect(x, y, w[j], h, INFILL)
                    if (!total && b.kinds[j] == CK.KEY) p.rect(x, y, w[j], h, KEYFILL)
                    val numeric = c.value is Number && (b.kinds[j] != CK.FIXED || j > 0)
                    val col = when (c.status) { Status.OK -> OKC; Status.BAD -> BADC; else -> INK }
                    val bold = b.kinds[j] == CK.KEY || total
                    lines[j].forEachIndexed { k, l ->
                        if (numeric) p.text(x + w[j] - pad, y + 3 + k * (sz + 2.5f), l, sz, bold, col, 2)
                        else p.text(x + pad, y + 3 + k * (sz + 2.5f), l, sz, bold, col)
                    }
                    x += w[j]
                }
                y += h
            }
            b.rows.forEachIndexed { i, row -> drawRow(row, i, false) }
            b.totals?.let { drawRow(it, 0, true) }
            p.line(mg, y, mg + avail, y, RULE, 0.6f)
            y += 12f
        }

        fun overview(b: BranchReport) {
            val sh = p.schematicHeight(b.id, avail)
            if (sh <= 0f) return
            ensure(sh + 60f)
            p.rect(mg, y, 4f, 22f, accent)
            p.text(mg + 12, y + 3, "${b.title} – system overview", 13f, true, INK)
            y += 32f
            y += p.schematic(b.id, mg, y, avail) + 6f
            val parts = p.schematicParts(b.id)
            if (parts.isNotEmpty()) {
                val colW = (avail - 12f) / 2; val half = (parts.size + 1) / 2
                val left = parts.take(half); val right = parts.drop(half)
                var ly = y; var ry = y
                fun col(items: List<String>, x: Float, start: Float): Float {
                    var yy = start
                    items.forEach { t ->
                        val lines = wrap(p, t, 7.6f, false, colW - 4)
                        if (yy + lines.size * 9.5f > bottom) return yy
                        lines.forEachIndexed { k, l -> p.text(x, yy + k * 9.5f, l, 7.6f, false, INK) }
                        yy += lines.size * 9.5f + 2f
                    }
                    return yy
                }
                ensure(minOf(half * 12f, 200f))
                ly = col(left, mg, y); ry = col(right, mg + colW + 12f, y)
                y = maxOf(ly, ry) + 10f
            }
        }

        var lastBranch: String? = null
        tools.forEachIndexed { ti, (b, t) ->
            if (b.id != lastBranch) { lastBranch = b.id; overview(b) }
            ensure(110f)
            if (ti > 0) y += 8f
            p.rect(mg, y, 4f, 30f, accent)
            p.text(mg + 12, y + 1, "${ti + 1}.  " + (if (multi) "${b.title} – " else "") + t.title, 13f, true, INK)
            p.text(mg + 12, y + 18, t.sub, 8.5f, false, MUTED)
            y += 40f
            if (t.tiles.isNotEmpty()) {
                val n = t.tiles.size; val gap = 6f; val tw = (avail - gap * (n - 1)) / n
                ensure(44f)
                t.tiles.forEachIndexed { i, k ->
                    val x0 = mg + i * (tw + gap)
                    p.rect(x0, y, tw, 38f, 0xFFF2F5F9.toInt(), RULE)
                    p.text(x0 + 7, y + 6, k.label, 7f, false, MUTED)
                    val v = k.cell.text + if (k.unit.isNotEmpty()) " " + k.unit else ""
                    var s = 12f
                    while (s > 7f && p.measure(v, s, true) > tw - 12) s -= 0.5f
                    p.text(x0 + 7, y + 19, v, s, true, when (k.cell.status) { Status.OK -> OKC; Status.BAD -> BADC; else -> INK })
                }
                y += 48f
            }
            t.blocks.forEach { bl -> when (bl) { is RKv -> kv(bl); is RTable -> table(bl); is RNote -> note(bl.text) } }
        }
        p.endPage()
    }
}
