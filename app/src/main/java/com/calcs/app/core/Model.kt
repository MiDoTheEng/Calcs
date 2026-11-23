package com.calcs.app.core

import kotlin.math.abs
import kotlin.math.roundToLong

/* ------------------------------------------------------------------ */
/*  Inputs are kept exactly as typed (strings); numbers are parsed     */
/*  when calculating. A grid is a list of rows, each a key→text map.   */
/* ------------------------------------------------------------------ */

typealias RowData = MutableMap<String, String>

class ToolState(
    val f: MutableMap<String, String> = linkedMapOf(),
    val lists: MutableMap<String, MutableList<RowData>> = linkedMapOf()
) {
    fun s(k: String): String = f[k] ?: ""
    fun n(k: String): Double = parseNum(f[k])
    fun n0(k: String): Double = n(k).let { if (it.isNaN()) 0.0 else it }
    fun list(k: String): MutableList<RowData> = lists.getOrPut(k) { mutableListOf() }
    fun set(k: String, v: Any?) { f[k] = toText(v) }

    fun copy(): ToolState = ToolState(
        LinkedHashMap(f),
        lists.mapValuesTo(linkedMapOf()) { (_, l) -> l.map { LinkedHashMap(it) as RowData }.toMutableList() }
    )
}

fun RowData.n(k: String): Double = parseNum(this[k])
fun RowData.n0(k: String): Double = n(k).let { if (it.isNaN()) 0.0 else it }
fun RowData.s(k: String): String = this[k] ?: ""

fun parseNum(t: String?): Double {
    if (t == null) return Double.NaN
    val x = t.trim().replace(',', '.')
    if (x.isEmpty()) return Double.NaN
    return x.toDoubleOrNull() ?: Double.NaN
}

fun toText(v: Any?): String = when (v) {
    null -> ""
    is Double -> if (v.isNaN()) "" else if (abs(v - v.roundToLong()) < 1e-9 && abs(v) < 1e12) v.roundToLong().toString() else v.toString()
    is Float -> toText(v.toDouble())
    is Number -> v.toString()
    else -> v.toString()
}

fun row(vararg p: Pair<String, Any?>): RowData = linkedMapOf<String, String>().apply { p.forEach { put(it.first, toText(it.second)) } }

fun state(vararg p: Pair<String, Any?>, lists: Map<String, List<RowData>> = emptyMap()): ToolState {
    val st = ToolState()
    p.forEach { st.f[it.first] = toText(it.second) }
    lists.forEach { (k, v) -> st.lists[k] = v.map { LinkedHashMap(it) as RowData }.toMutableList() }
    return st
}

/* Results: a bag of values plus per-row values (for grids). */
class Res {
    val m = HashMap<String, Any?>()
    val rows = ArrayList<HashMap<String, Any?>>()
    val lists = HashMap<String, ArrayList<HashMap<String, Any?>>>()
    fun list(k: String) = lists.getOrPut(k) { ArrayList() }
    operator fun get(k: String): Any? = m[k]
    operator fun set(k: String, v: Any?) { m[k] = v }
    fun d(k: String): Double = (m[k] as? Number)?.toDouble() ?: Double.NaN
    fun row(i: Int, k: String): Any? = rows.getOrNull(i)?.get(k)
}

/* ------------------------------------------------------------------ */
/*  Tool schema                                                        */
/* ------------------------------------------------------------------ */

enum class Kind { NUM, TEXT, SELECT }

data class Opt(val value: String, val label: String)

fun opts(vararg v: String) = v.map { Opt(it, it) }
fun optPairs(vararg v: Pair<Any, String>) = v.map { Opt(toText(it.first), it.second) }

class Field(
    val k: String,
    val label: String,
    val unit: String = "",
    val hint: String = "",
    val kind: Kind = Kind.NUM,
    val options: List<Opt> = emptyList(),
    val width: Int = 0,                       // preferred width in dp for grid columns (0 = default)
    val structural: Boolean = false,          // changes the layout (e.g. number of rows)
    val onChange: ((ToolState) -> Unit)? = null
)

class RRow(val label: String, val value: Any?, val unit: String = "", val dec: Int = 2, val key: Boolean = false)

class Out(val label: String, val dec: Int = 2, val key: Boolean = false, val summary: Boolean = false, val get: (Res, Int) -> Any?)

class FixedCol(val label: String, val dec: Int = -1)

class Action(val label: String, val run: (ToolState) -> Unit)

sealed class Block
class Section(
    val title: String,
    val fields: List<Field>,
    val results: ((ToolState, Res) -> List<RRow>)? = null,
    val action: Action? = null
) : Block()

class Grid(
    val title: String,
    val key: String,
    val cols: List<Field>,
    val outs: List<Out>,
    val help: String = "",
    val fixed: List<List<Any>>? = null,
    val fixedCols: List<FixedCol> = emptyList(),
    val add: (() -> RowData)? = null,
    val count: ((ToolState) -> Int)? = null,
    val rowLabel: ((Int) -> String)? = null,
    val titleKey: String? = null,
    val totals: ((Res) -> List<Pair<String, Any?>>)? = null,
    val results: ((ToolState, Res) -> List<RRow>)? = null,
    val skipEmpty: Boolean = false,
    val minRows: Int = 1
) : Block()

class Note(val text: String) : Block()
object SketchBlock : Block()

class ToolDef(
    val id: String,
    val title: String,
    val sub: String,
    val defaults: () -> ToolState,
    val blank: (() -> ToolState)? = null,
    val compute: (ToolState) -> Res,
    val tiles: (ToolState, Res) -> List<RRow> = { _, _ -> emptyList() },
    val blocks: List<Block>
)

class Branch(val id: String, val title: String, val short: String, val accent: Long, val desc: String, val tools: List<ToolDef>)

/* ------------------------------------------------------------------ */
/*  Formatting                                                         */
/* ------------------------------------------------------------------ */

fun fmt(v: Any?, dec: Int = 2): String = when (v) {
    null -> ""
    is Double -> if (v.isNaN() || v.isInfinite()) "—" else fmtNum(v, dec)
    is Float -> fmt(v.toDouble(), dec)
    is Int -> fmtNum(v.toDouble(), 0)
    is Long -> fmtNum(v.toDouble(), 0)
    else -> v.toString()
}

fun fmtNum(v: Double, dec: Int): String {
    val d = if (dec < 0) 2 else dec
    val s = String.format(java.util.Locale.US, "%,.${d}f", v)
    return if (s == "-0" || s.matches(Regex("-0\\.0+"))) s.substring(1) else s
}

enum class Status { NONE, OK, BAD }

fun statusOf(v: Any?): Status {
    if (v !is String) return Status.NONE
    val l = v.lowercase()
    if (listOf("too", "upsize", "outside", "above", "small", "warning", "fix", "combined", "high", "exceed").any { l.contains(it) }) return Status.BAD
    if (l.startsWith("ok") || l.startsWith("good") || l.startsWith("all ok")) return Status.OK
    return Status.NONE
}

fun inch(v: Any?): Any? {
    if (v !is Double) return v
    return when (v) {
        1.25 -> "1¼\""; 1.5 -> "1½\""; 2.5 -> "2½\""
        else -> toText(v) + "\""
    }
}
