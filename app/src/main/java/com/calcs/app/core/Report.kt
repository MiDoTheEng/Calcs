package com.calcs.app.core

/* Report model: what goes into the PDF and the Excel file. */

class Project(var name: String = "", var location: String = "", var engineer: String = "", var client: String = "", var date: String = "")

enum class CK { FIXED, IN, OUT, KEY }

class Cell(val text: String, val value: Any? = null, val dec: Int = -1, val status: Status = Status.NONE)

sealed class RBlock
class RKv(val title: String, val rows: List<KvLine>) : RBlock()
class KvLine(val label: String, val cell: Cell, val unit: String, val kind: CK)
class RTable(val title: String, val head: List<String>, val kinds: List<CK>, val rows: List<List<Cell>>, val totals: List<Cell>?) : RBlock()
class RNote(val text: String) : RBlock()

class ToolReport(val title: String, val sub: String, val tiles: List<KvLine>, val blocks: List<RBlock>)
class BranchReport(val id: String, val short: String, val title: String, val accent: Long, val tools: List<ToolReport>)
class ReportModel(val project: Project, val date: String, val branches: List<BranchReport>)

object Reports {
    fun optLabel(f: Field, v: String): String = f.options.firstOrNull { it.value == v || (parseNum(it.value) == parseNum(v) && !parseNum(v).isNaN()) }?.label ?: v

    private fun inCell(f: Field, v: String): Cell = when (f.kind) {
        Kind.SELECT -> optLabel(f, v).let { Cell(if (it == "—") "" else it, it) }
        Kind.TEXT -> Cell(v, v)
        Kind.NUM -> { val n = parseNum(v); Cell(v, if (n.isNaN()) null else n, decimals(v)) }
    }
    fun decimals(v: String): Int { val i = v.indexOf('.'); return if (i < 0) 0 else (v.length - i - 1).coerceAtMost(4) }

    fun rr(x: RRow) = KvLine(x.label, Cell(fmt(x.value, x.dec), x.value, x.dec, statusOf(x.value)), x.unit, if (x.key) CK.KEY else CK.OUT)

    fun rowCount(b: Grid, s: ToolState): Int = b.fixed?.size ?: b.count?.invoke(s) ?: s.list(b.key).size

    fun gridHead(b: Grid): List<String> = buildList {
        if (b.rowLabel != null) add("#")
        b.fixedCols.forEach { add(it.label) }
        b.cols.forEach { add(if (it.unit.isNotEmpty()) "${it.label} (${it.unit})" else it.label) }
        b.outs.forEach { add(it.label) }
    }

    fun isEmptyRow(b: Grid, row: RowData?): Boolean = row == null || b.cols.all { val n = row.n(it.k); n.isNaN() || n == 0.0 }

    fun tool(t: ToolDef, s: ToolState): ToolReport {
        val r = t.compute(s)
        val blocks = mutableListOf<RBlock>()
        t.blocks.forEach { b ->
            when (b) {
                is Section -> {
                    val lines = b.fields.map { f -> KvLine(f.label, inCell(f, s.s(f.k)), f.unit, CK.IN) } + (b.results?.invoke(s, r) ?: emptyList()).map { rr(it) }
                    if (lines.isNotEmpty()) blocks.add(RKv(b.title, lines))
                }
                is Grid -> {
                    val kinds = buildList {
                        if (b.rowLabel != null) add(CK.FIXED)
                        b.fixedCols.forEach { _ -> add(CK.FIXED) }
                        b.cols.forEach { _ -> add(CK.IN) }
                        b.outs.forEach { add(if (it.key) CK.KEY else CK.OUT) }
                    }
                    val list = s.list(b.key)
                    val rows = mutableListOf<List<Cell>>()
                    for (i in 0 until rowCount(b, s)) {
                        val row = list.getOrNull(i)
                        if (b.skipEmpty && isEmptyRow(b, row)) continue
                        val cells = mutableListOf<Cell>()
                        b.rowLabel?.let { cells.add(Cell(it(i), it(i))) }
                        b.fixed?.get(i)?.forEachIndexed { j, v -> cells.add(Cell(fmt(v, b.fixedCols[j].dec), v, b.fixedCols[j].dec)) }
                        b.cols.forEach { c -> cells.add(inCell(c, row?.s(c.k) ?: "")) }
                        b.outs.forEach { o -> val v = o.get(r, i); cells.add(Cell(fmt(v, o.dec), v, o.dec, statusOf(v))) }
                        rows.add(cells)
                    }
                    val totals = b.totals?.let { tf ->
                        val map = tf(r).toMap()
                        val head = gridHead(b)
                        head.mapIndexed { j, h ->
                            val outIdx = j - (head.size - b.outs.size)
                            val key = if (outIdx >= 0) b.outs[outIdx].label else null
                            when {
                                j == 0 -> Cell("TOTAL", "TOTAL")
                                key != null && map.containsKey(key) -> Cell(fmt(map[key], b.outs[outIdx].dec), map[key], b.outs[outIdx].dec)
                                else -> Cell("")
                            }
                        }
                    }
                    blocks.add(RTable(b.title, gridHead(b), kinds, rows, totals))
                    b.results?.invoke(s, r)?.let { res -> if (res.isNotEmpty()) blocks.add(RKv("Results", res.map { rr(it) })) }
                }
                is Note -> blocks.add(RNote(b.text))
                SketchBlock -> {}
            }
        }
        return ToolReport(t.title, t.sub, t.tiles(s, r).map { rr(it) }, blocks)
    }

    fun build(branches: List<Branch>, states: Map<String, ToolState>, project: Project, date: String) = ReportModel(project, date,
        branches.map { b -> BranchReport(b.id, b.short, b.title, b.accent, b.tools.map { t -> tool(t, states[t.id] ?: t.defaults()) }) })
}
