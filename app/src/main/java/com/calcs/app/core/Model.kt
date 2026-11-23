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
    val action: Action? = null,
    val diagram: String? = null               // id of the input diagram drawn above the fields
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
    val minRows: Int = 1,
    val icon: ((Int) -> String)? = null,      // icon per row (fixture lists)
    val picker: String? = null,                // column edited by the visual picker (− / +)
    val readOnly: Boolean = false              // quantities come from elsewhere (the rooms) – shown, not typed
) : Block()

/** A room type the user can add (bathroom, kitchen…) with its usual fixtures (index in the code table → qty). */
class RoomTemplate(val name: String, val icon: String, val fx: List<Pair<Int, Int>>, val common: Boolean = false)

/**
 * Fixtures entered room by room: "20 apartments, each with 2 bathrooms + 1 kitchen".
 * Rooms are rows of [key] (name, icon, n, where = apt|common, fx = "i:q,i:q"); the code-table grid [table]
 * is filled from them (Rooms.apply) and shown read-only as the totals.
 */
class Rooms(
    val title: String, val key: String, val table: String,
    val labels: List<List<String>>,          // per code-table row: name, variant text
    val icons: List<String>,
    val templates: List<RoomTemplate>,
    val perUnit: Boolean,                    // true: rooms inside each apartment × number of apartments + rooms outside
    val help: String = "",
    val total: ((Res) -> List<RRow>)? = null // live result shown under the rooms
) : Block() {
    companion object {
        fun fx(r: RowData): List<Pair<Int, Double>> = r.s("fx").split(',').mapNotNull { p ->
            val a = p.split(':'); val i = a.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            i to (a.getOrNull(1)?.let { parseNum(it) }?.takeIf { !it.isNaN() } ?: 0.0)
        }
        fun setFx(r: RowData, l: List<Pair<Int, Double>>) { r["fx"] = l.joinToString(",") { "${it.first}:${toText(it.second)}" } }
        fun room(t: RoomTemplate, n: Int = 1) = row("name" to t.name, "icon" to t.icon, "n" to n, "where" to if (t.common) "common" else "apt",
            "fx" to t.fx.joinToString(",") { "${it.first}:${it.second}" })

        /** Fills the code-table grid from the rooms. Old projects (no rooms yet) are converted once. */
        fun apply(s: ToolState, roomsKey: String, tableKey: String, size: Int, unitKey: String, extraKey: String?) {
            val rooms = s.list(roomsKey); val tab = s.list(tableKey)
            while (tab.size < size) tab.add(linkedMapOf())
            if (s.f["roomsOn"] != "1") {
                s.f["roomsOn"] = "1"
                if (rooms.isEmpty()) {
                    val inUnit = (0 until size).mapNotNull { i -> tab[i].n0(unitKey).takeIf { it > 0 }?.let { i to it } }
                    val outside = if (extraKey == null) emptyList() else (0 until size).mapNotNull { i -> tab[i].n0(extraKey).takeIf { it > 0 }?.let { i to it } }
                    if (inUnit.isNotEmpty()) rooms.add(row("name" to if (extraKey == null) "Fixtures" else "Apartment fixtures", "icon" to "bathgroup", "n" to 1, "where" to "apt").also { setFx(it, inUnit) })
                    if (outside.isNotEmpty()) rooms.add(row("name" to "Outside the apartments", "icon" to "wc", "n" to 1, "where" to "common").also { setFx(it, outside) })
                }
            }
            val unit = DoubleArray(size); val extra = DoubleArray(size)
            rooms.forEach { r ->
                val n = r.n0("n")
                fx(r).forEach { (i, q) -> if (i in 0 until size) { if (extraKey != null && r.s("where") == "common") extra[i] += n * q else unit[i] += n * q } }
            }
            for (i in 0 until size) { tab[i][unitKey] = toText(unit[i]); if (extraKey != null) tab[i][extraKey] = toText(extra[i]) }
        }
    }
}

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

/** Icon key for a fixture name (used by the visual pickers). */
fun fixtureIcon(name: String): String {
    val n = name.lowercase()
    return when {
        n.startsWith("bathroom group") -> "bathgroup"
        n.contains("water closet") -> "wc"
        n.contains("urinal") -> "urinal"
        n.contains("lavatory") -> "lav"
        n.contains("bathtub") -> "bath"
        n.contains("shower") -> "shower"
        n.contains("bidet") -> "bidet"
        n.contains("kitchen sink") || n.contains("pantry") || n.contains("combination") -> "sink"
        n.contains("service sink") || n.contains("laundry") -> "service"
        n.contains("dishwash") -> "dishwasher"
        n.contains("washing machine") || n.contains("clothes washer") -> "washer"
        n.contains("drinking") -> "fountain"
        n.contains("floor drain") -> "floordrain"
        else -> "sink"
    }
}
