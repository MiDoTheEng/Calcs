package com.calcs.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calcs.app.core.Catalog
import com.calcs.app.core.Project
import com.calcs.app.core.RowData
import com.calcs.app.core.ToolDef
import com.calcs.app.core.ToolState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed class Screen {
    object Home : Screen()
    data class BranchScr(val branchId: String) : Screen()
    object Units : Screen()
}

/** Everything the user typed, kept on disk so it survives closing the app. */
class AppState(private val ctx: Context) {
    val states = HashMap<String, ToolState>()
    var project = Project()
    val units = HashMap<String, Pair<String, String>>()       // group → (value, unit)
    val lastTab = HashMap<String, Int>()

    /** bumped on every edit → results recompute */
    var version by mutableIntStateOf(0)
        private set
    /** bumped when inputs are replaced (example / clear / new project) → text fields reload */
    var generation by mutableIntStateOf(0)
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)

    private val file = File(ctx.filesDir, "calcs.json")
    private val handler = Handler(Looper.getMainLooper())
    private val saver = Runnable { saveNow() }

    init { load() }

    fun state(t: ToolDef): ToolState = states.getOrPut(t.id) { t.defaults() }

    fun touch() { version++; handler.removeCallbacks(saver); handler.postDelayed(saver, 600) }
    fun reload() { generation++; touch() }

    fun resetTool(t: ToolDef, blank: Boolean) {
        states[t.id] = if (blank) (t.blank ?: t.defaults)() else t.defaults()
        reload()
    }

    fun newProject() {
        states.clear(); Catalog.TOOLS.forEach { states[it.id] = it.blank?.invoke() ?: it.defaults() }
        project = Project(); reload()
    }

    fun loadExamples() {
        Catalog.TOOLS.forEach { states[it.id] = it.defaults() }
        reload()
    }

    /* ------------------------------ persistence ------------------------------ */
    private fun load() {
        Catalog.TOOLS.forEach { states[it.id] = it.defaults() }
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            o.optJSONObject("project")?.let { p ->
                project = Project(p.optString("name"), p.optString("location"), p.optString("engineer"), p.optString("client"), p.optString("date"))
            }
            val tools = o.optJSONObject("tools")
            Catalog.TOOLS.forEach { t ->
                val js = tools?.optJSONObject(t.id) ?: return@forEach
                val st = t.defaults()
                js.optJSONObject("f")?.let { f -> f.keys().forEach { k -> st.f[k] = f.optString(k) } }
                js.optJSONObject("lists")?.let { ls ->
                    ls.keys().forEach { k ->
                        val arr = ls.optJSONArray(k) ?: return@forEach
                        val rows = mutableListOf<RowData>()
                        for (i in 0 until arr.length()) {
                            val ro = arr.optJSONObject(i) ?: continue
                            val m: RowData = linkedMapOf(); ro.keys().forEach { kk -> m[kk] = ro.optString(kk) }; rows.add(m)
                        }
                        val def = st.lists[k]
                        // fixed lists (fixtures, orientations) must keep their length
                        if (def != null && isFixed(t, k) && rows.size != def.size) return@forEach
                        if (rows.isNotEmpty()) st.lists[k] = rows
                    }
                }
                states[t.id] = st
            }
            o.optJSONObject("units")?.let { u -> u.keys().forEach { k -> u.optJSONArray(k)?.let { a -> units[k] = a.optString(0) to a.optString(1) } } }
            o.optJSONObject("tabs")?.let { u -> u.keys().forEach { k -> lastTab[k] = u.optInt(k) } }
        } catch (_: Exception) { /* corrupt file: start from the examples */ }
    }

    private fun isFixed(t: ToolDef, key: String) = t.blocks.any { it is com.calcs.app.core.Grid && it.key == key && (it.fixed != null || it.count != null) }

    fun saveNow() {
        try {
            val o = JSONObject()
            o.put("project", JSONObject().put("name", project.name).put("location", project.location).put("engineer", project.engineer).put("client", project.client).put("date", project.date))
            val tools = JSONObject()
            states.forEach { (id, st) ->
                val js = JSONObject()
                val f = JSONObject(); st.f.forEach { (k, v) -> f.put(k, v) }; js.put("f", f)
                val ls = JSONObject()
                st.lists.forEach { (k, rows) -> val a = JSONArray(); rows.forEach { r -> val ro = JSONObject(); r.forEach { (kk, vv) -> ro.put(kk, vv) }; a.put(ro) }; ls.put(k, a) }
                js.put("lists", ls); tools.put(id, js)
            }
            o.put("tools", tools)
            val u = JSONObject(); units.forEach { (k, v) -> u.put(k, JSONArray().put(v.first).put(v.second)) }; o.put("units", u)
            val tb = JSONObject(); lastTab.forEach { (k, v) -> tb.put(k, v) }; o.put("tabs", tb)
            val tmp = File(file.parentFile, "calcs.json.tmp"); tmp.writeText(o.toString()); tmp.renameTo(file)
        } catch (_: Exception) { }
    }
}
