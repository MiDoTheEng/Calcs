package com.calcs.app.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/* Minimal .xlsx writer: inline strings, a handful of styles, number formats per decimals. */
object Xlsx {
    // style bases: fontId, fillId, borderId, wrap
    private val BASE = mapOf(
        "normal" to intArrayOf(0, 0, 0, 0), "title" to intArrayOf(1, 0, 0, 0), "head" to intArrayOf(2, 2, 1, 1), "input" to intArrayOf(0, 3, 1, 0),
        "result" to intArrayOf(0, 4, 1, 0), "key" to intArrayOf(3, 5, 1, 0), "note" to intArrayOf(4, 0, 0, 1), "section" to intArrayOf(5, 0, 0, 0),
        "sub" to intArrayOf(4, 0, 0, 0), "total" to intArrayOf(3, 6, 1, 0), "ok" to intArrayOf(6, 4, 1, 0), "bad" to intArrayOf(7, 4, 1, 0),
        "label" to intArrayOf(0, 0, 1, 1), "fixed" to intArrayOf(0, 0, 1, 0)
    )
    private const val FONTS = "<fonts count=\"8\"><font><sz val=\"10\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"15\"/><color rgb=\"FF1B2430\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"9\"/><color rgb=\"FFFFFFFF\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"10\"/><name val=\"Arial\"/></font>" +
        "<font><i/><sz val=\"9\"/><color rgb=\"FF5D6B7D\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"11\"/><color rgb=\"FF1F4E78\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"10\"/><color rgb=\"FF1D7A3A\"/><name val=\"Arial\"/></font>" +
        "<font><b/><sz val=\"10\"/><color rgb=\"FFB3261E\"/><name val=\"Arial\"/></font></fonts>"
    private const val FILLS = "<fills count=\"7\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF27364A\"/></patternFill></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFF2CC\"/></patternFill></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE2EFDA\"/></patternFill></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFC6E0B4\"/></patternFill></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE6EBF1\"/></patternFill></fill></fills>"
    private const val BORDERS = "<borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FFBFC8D3\"/></left><right style=\"thin\"><color rgb=\"FFBFC8D3\"/></right>" +
        "<top style=\"thin\"><color rgb=\"FFBFC8D3\"/></top><bottom style=\"thin\"><color rgb=\"FFBFC8D3\"/></bottom></border></borders>"

    private class Styles {
        val xfs = mutableListOf<String>(); val map = HashMap<String, Int>()
        init { get("normal", -1) }
        fun get(name: String, dec: Int): Int {
            val key = "$name|$dec"
            map[key]?.let { return it }
            val b = BASE[name] ?: BASE.getValue("normal")
            val nf = if (dec < 0) 0 else 164 + dec.coerceIn(0, 4)
            val wrap = b[3] == 1
            xfs.add("<xf numFmtId=\"$nf\" fontId=\"${b[0]}\" fillId=\"${b[1]}\" borderId=\"${b[2]}\" xfId=\"0\"" + (if (nf > 0) " applyNumberFormat=\"1\"" else "") +
                " applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"" + (if (wrap) " wrapText=\"1\"" else "") + "/></xf>")
            map[key] = xfs.size - 1
            return xfs.size - 1
        }
        fun xml() = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"5\"><numFmt numFmtId=\"164\" formatCode=\"#,##0\"/><numFmt numFmtId=\"165\" formatCode=\"#,##0.0\"/><numFmt numFmtId=\"166\" formatCode=\"#,##0.00\"/>" +
            "<numFmt numFmtId=\"167\" formatCode=\"0.000\"/><numFmt numFmtId=\"168\" formatCode=\"0.0000\"/></numFmts>" + FONTS + FILLS + BORDERS +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"${xfs.size}\">" + xfs.joinToString("") +
            "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").filter { it == '\t' || it == '\n' || it >= ' ' }
    private fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }; return sb.toString() }

    private class Sheet(val name: String, val st: Styles) {
        val cells = sortedMapOf<Int, java.util.TreeMap<Int, Pair<Any?, Int>>>()
        val widths = HashMap<Int, Double>(); val heights = HashMap<Int, Double>()
        fun set(r: Int, c: Int, v: Any?, style: String, dec: Int = -1) {
            val isNum = v is Number && !(v.toDouble().isNaN() || v.toDouble().isInfinite())
            cells.getOrPut(r) { java.util.TreeMap() }[c] = (if (isNum) (v as Number).toDouble() else v?.toString() ?: "") to st.get(style, if (isNum) (if (dec < 0) guess(v as Number) else dec) else -1)
            if (style !in setOf("title", "note", "sub", "section")) {
                val len = if (isNum) 10.0 else (v?.toString()?.length ?: 0).toDouble()
                widths[c] = maxOf(widths[c] ?: 8.0, minOf(len + 2, 46.0))
            }
        }
        fun xml(): String {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            sb.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>")
            if (widths.isNotEmpty()) {
                sb.append("<cols>")
                for (c in 0..(widths.keys.maxOrNull() ?: 0)) sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"${maxOf(widths[c] ?: 9.0, 8.0)}\" customWidth=\"1\"/>")
                sb.append("</cols>")
            }
            sb.append("<sheetData>")
            cells.forEach { (r, row) ->
                sb.append("<row r=\"${r + 1}\"" + (heights[r]?.let { " ht=\"$it\" customHeight=\"1\"" } ?: "") + ">")
                row.forEach { (c, p) ->
                    val ref = col(c) + (r + 1); val v = p.first; val s = p.second
                    when {
                        v is Double -> sb.append("<c r=\"$ref\" s=\"$s\"><v>$v</v></c>")
                        v == null || v == "" -> sb.append("<c r=\"$ref\" s=\"$s\"/>")
                        else -> sb.append("<c r=\"$ref\" s=\"$s\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData><pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
            sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\"/></worksheet>")
            return sb.toString()
        }
    }
    private fun guess(v: Number): Int { val a = kotlin.math.abs(v.toDouble()); return if (a >= 100) 1 else if (a >= 1) 2 else 3 }

    private fun styleFor(kind: CK, st: Status) = when {
        kind != CK.IN && st == Status.OK -> "ok"
        kind != CK.IN && st == Status.BAD -> "bad"
        kind == CK.IN -> "input"; kind == CK.KEY -> "key"; kind == CK.OUT -> "result"; else -> "fixed"
    }

    fun render(m: ReportModel): ByteArray {
        val st = Styles(); val sheets = mutableListOf<Sheet>(); val used = HashSet<String>()
        fun safe(n0: String): String {
            var n = n0.replace(Regex("[\\[\\]:*?/\\\\]"), " ").take(31).trim().ifEmpty { "Sheet" }
            val base = n; var k = 2
            while (n.lowercase() in used) n = base.take(28) + " " + k++
            used.add(n.lowercase()); return n
        }
        val p = m.project
        val sum = Sheet(safe("Summary"), st); sheets.add(sum)
        var r = 0
        sum.set(r, 0, "Calcs – MEP calculation summary", "title"); sum.heights[r] = 22.0; r++
        sum.set(r++, 0, "Educational tool – verify every value against the governing code edition and authority requirements.", "sub"); r++
        listOf("Project" to p.name, "Location" to p.location, "Prepared by" to p.engineer, "Client / reference" to p.client, "Date" to p.date.ifBlank { m.date })
            .filter { it.second.isNotBlank() }.forEach { sum.set(r, 0, it.first, "label"); sum.set(r, 1, it.second, "normal"); r++ }
        r++
        listOf("Branch", "Calculation", "Key result", "Value", "Unit").forEachIndexed { j, h -> sum.set(r, j, h, "head") }; r++
        m.branches.forEach { b -> b.tools.forEach { t -> t.tiles.forEachIndexed { i, k ->
            sum.set(r, 0, if (i == 0) b.title else "", "label"); sum.set(r, 1, if (i == 0) t.title else "", "label"); sum.set(r, 2, k.label, "label")
            if (k.cell.value is Number) sum.set(r, 3, k.cell.value, "key", k.cell.dec) else sum.set(r, 3, k.cell.text, "key")
            sum.set(r, 4, k.unit, "label"); r++
        } } }
        sum.widths[0] = 16.0; sum.widths[1] = 30.0; sum.widths[2] = 26.0; sum.widths[3] = 22.0; sum.widths[4] = 10.0

        m.branches.forEach { b -> b.tools.forEach { t ->
            val sh = Sheet(safe((if (m.branches.size > 1) b.short + " " else "") + t.title), st); sheets.add(sh)
            var rr = 0
            sh.set(rr, 0, "${b.title} – ${t.title}", "title"); sh.heights[rr] = 22.0; rr++
            sh.set(rr++, 0, "${t.sub}  ·  ${p.name.ifBlank { "Untitled project" }}  ·  ${p.date.ifBlank { m.date }}", "sub")
            sh.set(rr++, 0, "Yellow = inputs · light green = results · green = key results", "sub"); rr++
            if (t.tiles.isNotEmpty()) {
                sh.set(rr++, 0, "KEY RESULTS", "section")
                t.tiles.forEach { k ->
                    sh.set(rr, 0, k.label, "label")
                    if (k.cell.value is Number) sh.set(rr, 1, k.cell.value, styleFor(CK.KEY, k.cell.status), k.cell.dec) else sh.set(rr, 1, k.cell.text, styleFor(CK.KEY, k.cell.status))
                    sh.set(rr, 2, k.unit, "label"); rr++
                }
                rr++
            }
            t.blocks.forEach { bl ->
                when (bl) {
                    is RKv -> {
                        sh.set(rr++, 0, bl.title.uppercase(), "section")
                        bl.rows.forEach { x ->
                            sh.set(rr, 0, x.label, "label")
                            val v = x.cell.value
                            if (v is Number && !v.toDouble().isNaN()) sh.set(rr, 1, v, styleFor(x.kind, x.cell.status), x.cell.dec) else sh.set(rr, 1, x.cell.text, styleFor(x.kind, x.cell.status))
                            sh.set(rr, 2, x.unit, "label"); rr++
                        }
                        rr++
                    }
                    is RTable -> {
                        sh.set(rr++, 0, bl.title.uppercase(), "section")
                        bl.head.forEachIndexed { j, h -> sh.set(rr, j, h, "head") }; sh.heights[rr] = 30.0; rr++
                        bl.rows.forEach { row ->
                            row.forEachIndexed { j, c ->
                                val v = c.value; val style = styleFor(bl.kinds[j], c.status)
                                if (v is Number && !v.toDouble().isNaN()) sh.set(rr, j, v, style, c.dec) else sh.set(rr, j, c.text, style)
                            }
                            rr++
                        }
                        bl.totals?.let { tot -> tot.forEachIndexed { j, c -> if (c.value is Number) sh.set(rr, j, c.value, "total", c.dec) else sh.set(rr, j, c.text, "total") }; rr++ }
                        if (bl.rows.isEmpty()) sh.set(rr++, 0, "No rows entered.", "sub")
                        rr++
                    }
                    is RNote -> { sh.set(rr, 0, bl.text, "sub"); rr += 2 }
                }
            }
            sh.widths[0] = maxOf(sh.widths[0] ?: 0.0, 30.0).coerceAtMost(44.0)
        } }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, s: String) { z.putNextEntry(ZipEntry(name)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
                "</Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                sheets.mapIndexed { i, s -> "<sheet name=\"${esc(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
                "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", s.xml()) }
            put("xl/styles.xml", st.xml())
        }
        return out.toByteArray()
    }
}
