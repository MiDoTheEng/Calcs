@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.calcs.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.Branch
import com.calcs.app.core.Catalog
import com.calcs.app.core.Eng
import com.calcs.app.core.Grid
import com.calcs.app.core.Note
import com.calcs.app.core.Res
import com.calcs.app.core.Section
import com.calcs.app.core.SketchBlock
import com.calcs.app.core.ToolDef
import com.calcs.app.core.fmt
import com.calcs.app.core.parseNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CalcsApp(app: AppState) {
    when (val sc = app.screen) {
        is Screen.Home -> CalcsTheme { HomeScreen(app) }
        is Screen.Units -> CalcsTheme(0xFF5B5BD6) { UnitsScreen(app) }
        is Screen.BranchScr -> {
            val b = Catalog.BRANCHES.first { it.id == sc.branchId }
            CalcsTheme(b.accent) { BranchScreen(app, b) }
        }
    }
}

/* ================================ export ================================ */

/**
 * PDF / Excel buttons. Each opens a picker: on a branch screen → this calculation or the whole system;
 * on the home screen → pick systems, one file per system (or one combined file).
 */
@Composable
fun ExportActions(app: AppState, branches: List<Branch>, current: ToolDef? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<List<Saved>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) }          // null = closed, true = PDF, false = Excel
    var onlyThis by remember { mutableStateOf(true) }
    val chosen = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var separate by remember { mutableStateOf(true) }

    fun run(pdf: Boolean, jobs: List<List<Branch>>) {
        busy = true
        scope.launch {
            try {
                saved = withContext(Dispatchers.Default) { jobs.map { bs -> val (name, bytes) = Exporter.build(app, bs, pdf); Exporter.save(ctx, name, bytes) } }
            } catch (e: Exception) { error = e.message ?: e.toString() }
            busy = false
        }
    }
    fun sub(b: Branch, t: ToolDef) = Branch(b.id, b.title, b.short, b.accent, b.desc, listOf(t))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
        Button(onClick = { picking = true }, enabled = !busy) { Text("PDF", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = { picking = false }, enabled = !busy) { Text("Excel", fontWeight = FontWeight.Bold) }
    }

    picking?.let { pdf ->
        val kind = if (pdf) "PDF" else "Excel"
        val single = branches.size == 1
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("Export $kind") },
            text = {
                Column {
                    if (single) {
                        val b = branches[0]
                        if (current != null && b.tools.size > 1) {
                            ChoiceRow(onlyThis, true, "This calculation only", current.title) { onlyThis = true }
                            ChoiceRow(!onlyThis, true, "Whole ${b.title} system", "${b.tools.size} calculations in one file") { onlyThis = false }
                        } else Text("${b.title} – all calculations in one file")
                    } else {
                        Text("Pick the systems to export", style = T.small, color = LocalCalc.current.muted)
                        Spacer(Modifier.height(6.dp))
                        branches.forEach { b ->
                            ChoiceRow(chosen[b.id] == true, false, b.title, b.tools.joinToString(" · ") { it.title }) { chosen[b.id] = chosen[b.id] != true }
                        }
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(separate, false, "One file per system", "Off = all picked systems in one file") { separate = !separate }
                    }
                }
            },
            confirmButton = {
                val picked = branches.filter { chosen[it.id] == true }
                TextButton(enabled = single || picked.isNotEmpty(), onClick = {
                    picking = null
                    val jobs: List<List<Branch>> = if (single) {
                        val b = branches[0]
                        if (current != null && b.tools.size > 1 && onlyThis) listOf(listOf(sub(b, current))) else listOf(listOf(b))
                    } else if (separate) picked.map { listOf(it) } else listOf(picked)
                    run(pdf, jobs)
                }) { Text("Export", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        )
    }

    if (busy) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Preparing…") },
        text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Building the calculation report") } })
    if (saved.isNotEmpty()) {
        val list = saved
        AlertDialog(onDismissRequest = { saved = emptyList() },
            title = { Text(if (list.size == 1) "Saved to Downloads/Calcs" else "${list.size} files saved to Downloads/Calcs") },
            text = { Column { list.forEach { Text(it.name, style = T.small) } } },
            confirmButton = {
                Row {
                    TextButton(onClick = { saved = emptyList(); Exporter.shareMany(ctx, list) }) { Text("Share") }
                    if (list.size == 1) TextButton(onClick = { saved = emptyList(); Exporter.open(ctx, list[0]) }) { Text("Open") }
                }
            },
            dismissButton = { TextButton(onClick = { saved = emptyList() }) { Text("Done") } })
    }
    error?.let { e -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Could not export") }, text = { Text(e) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun ChoiceRow(on: Boolean, radio: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (radio) androidx.compose.material3.RadioButton(selected = on, onClick = onClick)
        else androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { onClick() })
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = T.small, color = LocalCalc.current.muted)
        }
    }
}

@Composable
fun Confirm(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onOk() }) { Text(ok, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/* ================================= home ================================= */

@Composable
fun HomeScreen(app: AppState) {
    val cc = LocalCalc.current
    var ask by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = cc.bg,
        topBar = {
            TopAppBar(
                title = { Column { Text("Calcs", fontWeight = FontWeight.ExtraBold); Text("MEP design calculations", style = T.small, color = cc.muted) } },
                actions = { ExportActions(app, Catalog.BRANCHES) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cc.surface)
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    key(app.generation) { Text(app.project.name.ifBlank { "New project" }, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = cc.ink) }
                    Text("Pick a branch, enter your data, export the calculation as PDF or Excel.", color = cc.muted)
                }
            }
            Catalog.BRANCHES.forEach { b -> item { BranchCard(b.short, b.title, b.desc, b.tools.map { it.title }, accentFor(b.accent, false)) { app.screen = Screen.BranchScr(b.id) } } }
            item { BranchCard("U", "Unit converter", "Flow, pressure, length, volume, area, power, velocity, sprinkler density & K-factor", emptyList(), Color(0xFF5B5BD6)) { app.screen = Screen.Units } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel("Project details (printed on every report)") {
                    key(app.generation) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProjField("Project name", app.project.name) { app.project.name = it; app.touch() }
                            ProjField("Location", app.project.location) { app.project.location = it; app.touch() }
                            ProjField("Prepared by", app.project.engineer) { app.project.engineer = it; app.touch() }
                            ProjField("Client / reference", app.project.client) { app.project.client = it; app.touch() }
                            ProjField("Date (blank = today)", app.project.date) { app.project.date = it; app.touch() }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ask = "new" }) { Icon(Icons.Filled.Clear, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New blank project") }
                        OutlinedButton(onClick = { ask = "examples" }) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load all worked examples") }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Educational tool – verify every value against the governing code edition and the authority requirements.", style = T.small, color = cc.muted, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
    when (ask) {
        "new" -> Confirm("Start a new blank project?", "Clears the project details and empties every calculation.", "Clear everything", { app.newProject() }) { ask = null }
        "examples" -> Confirm("Load the worked examples?", "Replaces the inputs of every calculation with the example values.", "Load examples", { app.loadExamples() }) { ask = null }
    }
}

@Composable
private fun ProjField(label: String, value: String, onChange: (String) -> Unit) {
    var v by remember { mutableStateOf(value) }
    OutlinedTextField(value = v, onValueChange = { v = it; onChange(it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.width(260.dp))
}

@Composable
private fun BranchCard(code: String, title: String, desc: String, tools: List<String>, color: Color, onClick: () -> Unit) {
    val cc = LocalCalc.current
    Surface(shape = RoundedCornerShape(18.dp), color = cc.surface, border = androidx.compose.foundation.BorderStroke(1.dp, cc.line),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(18.dp).heightIn(min = 170.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text(code, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = cc.ink)
                Text(desc, style = T.small, color = cc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tools.isNotEmpty()) Text(tools.joinToString("  ·  "), fontSize = 12.sp, color = cc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ================================ branch ================================ */

@Composable
fun BranchScreen(app: AppState, b: Branch) {
    val cc = LocalCalc.current
    BackHandler { app.saveNow(); app.screen = Screen.Home }
    var tab by remember { mutableStateOf((app.lastTab[b.id] ?: 0).coerceIn(0, b.tools.size - 1)) }
    Scaffold(
        containerColor = cc.bg,
        topBar = {
            Column(Modifier.background(cc.surface)) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { app.saveNow(); app.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Column { Text(b.title, fontWeight = FontWeight.Bold); Text(app.project.name.ifBlank { "Untitled project" }, style = T.small, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    actions = { ExportActions(app, listOf(b), b.tools[tab]) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cc.surface)
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = cc.surface) {
                    b.tools.forEachIndexed { i, t ->
                        Tab(selected = i == tab, onClick = { tab = i; app.lastTab[b.id] = i; app.touch() },
                            text = { Text(t.title, fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal) })
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            key(b.tools[tab].id) { ToolContent(app, b.tools[tab]) }
        }
    }
}

@Composable
fun ToolContent(app: AppState, t: ToolDef) {
    val cc = LocalCalc.current
    val v = app.version                    // read → recompose on every edit
    val gen = app.generation
    val s = app.state(t)
    val r: Res = remember(v, gen, t.id) { try { t.compute(s) } catch (e: Exception) { Res() } }
    var ask by remember { mutableStateOf<String?>(null) }
    val onlyUsed = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w: Dp = minOf(maxWidth, 1240.dp)
        val narrow = w < 600.dp
        val wide = w >= 840.dp
        key(gen) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "head", contentType = "head") {
                    Column(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(bottom = 14.dp)) {
                        FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.widthIn(min = 240.dp)) {
                                Text(t.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cc.ink)
                                Text(t.sub, color = cc.muted)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { ask = "example" }) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Example") }
                                if (t.blank != null) OutlinedButton(onClick = { ask = "clear" }) { Icon(Icons.Filled.Clear, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Clear") }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Tiles(try { t.tiles(s, r) } catch (e: Exception) { emptyList() }, w - 32.dp)
                    }
                }
                t.blocks.forEachIndexed { bi, blk ->
                    if (blk is Grid) {
                        // header, each row and footer are separate lazy items → smooth scrolling on long lists
                        ensureRows(blk, s)
                        val lay = gridLayout(blk, w)
                        val only = onlyUsed[blk.key] == true
                        item(key = "g$bi-h", contentType = "grid-h") {
                            Part(PartPos.TOP) { GridHeader(blk, lay, only) { onlyUsed[blk.key] = !only } }
                        }
                        val list = s.list(blk.key)
                        for (i in 0 until com.calcs.app.core.Reports.rowCount(blk, s)) {
                            if (only && blk.fixed != null && !rowUsedPublic(blk, list[i])) continue
                            item(key = "g$bi-r$i", contentType = if (lay.table) "row-t$bi" else "row-c$bi") {
                                Part(PartPos.MID) { GridRow(blk, s, r, app, lay, i) }
                            }
                        }
                        item(key = "g$bi-f", contentType = "grid-f") {
                            Part(PartPos.BOTTOM) { GridFooter(blk, s, r, app, lay) }
                        }
                    } else {
                        item(key = "b$bi", contentType = blk::class.simpleName) {
                            Box(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(bottom = 14.dp)) {
                                when (blk) {
                                    is Section -> SectionView(app, blk, s, r, wide, narrow)
                                    is Note -> Text(blk.text, style = T.small, color = cc.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    SketchBlock -> BranchSketch(s, r, w)
                                    is Grid -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    when (ask) {
        "example" -> Confirm("Load the worked example?", "Replaces the inputs of “${t.title}” with the example values.", "Load example", { app.resetTool(t, false) }) { ask = null }
        "clear" -> Confirm("Clear this calculation?", "Empties the quantities and rows of “${t.title}” so you can enter your project.", "Clear", { app.resetTool(t, true) }) { ask = null }
    }
}

enum class PartPos { TOP, MID, BOTTOM }

/** A slice of a card that is split over several lazy items. */
@Composable
fun Part(pos: PartPos, content: @Composable () -> Unit) {
    val cc = LocalCalc.current
    val r = 16.dp
    val shape = when (pos) {
        PartPos.TOP -> RoundedCornerShape(topStart = r, topEnd = r)
        PartPos.MID -> androidx.compose.ui.graphics.RectangleShape
        PartPos.BOTTOM -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
    }
    Box(
        Modifier.widthIn(max = 1240.dp).fillMaxWidth()
            .padding(bottom = if (pos == PartPos.BOTTOM) 14.dp else 0.dp)
            .clip(shape).background(cc.surface)
            .padding(start = 16.dp, end = 16.dp, top = if (pos == PartPos.TOP) 16.dp else 0.dp, bottom = if (pos == PartPos.BOTTOM) 16.dp else 0.dp)
    ) { content() }
}

@Composable
private fun SectionView(app: AppState, b: Section, s: com.calcs.app.core.ToolState, r: Res, wide: Boolean, narrow: Boolean) {
    Panel(b.title) {
        val res = b.results?.let { f -> try { f(s, r) } catch (e: Exception) { emptyList() } } ?: emptyList()
        val inputs: @Composable () -> Unit = {
            Column {
                b.fields.forEach { f -> FieldRow(f, s.f, app, narrow, onStructural = { app.reload() }) }
                b.action?.let { a -> Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { a.run(s); app.reload() }) { Text(a.label) } }
            }
        }
        if (b.fields.isNotEmpty() && res.isNotEmpty() && wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { inputs() }
                Box(Modifier.weight(1f)) { ResultList(res) }
            }
        } else if (wide && res.isEmpty() && b.fields.size > 2) {
            // no results: spread the inputs over two columns
            val half = (b.fields.size + 1) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                listOf(b.fields.take(half), b.fields.drop(half)).forEach { part ->
                    Column(Modifier.weight(1f)) { part.forEach { f -> FieldRow(f, s.f, app, false, onStructural = { app.reload() }) } }
                }
            }
            b.action?.let { a -> Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { a.run(s); app.reload() }) { Text(a.label) } }
        } else if (wide && res.isEmpty()) {
            Box(Modifier.widthIn(max = 620.dp)) { inputs() }
        } else {
            if (b.fields.isNotEmpty()) inputs()
            if (res.isNotEmpty()) { if (b.fields.isNotEmpty()) Spacer(Modifier.height(12.dp)); ResultList(res) }
        }
    }
}

/* ================================= units ================================= */

@Composable
fun UnitsScreen(app: AppState) {
    val cc = LocalCalc.current
    BackHandler { app.saveNow(); app.screen = Screen.Home }
    Scaffold(
        containerColor = cc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { app.saveNow(); app.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Column { Text("Unit converter", fontWeight = FontWeight.Bold); Text("Type a value, pick its unit", style = T.small, color = cc.muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cc.surface)
            )
        }
    ) { pad ->
        val v = app.version
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Eng.UNITS.forEach { (group, units) ->
                item {
                    val cur = app.units[group] ?: ("1" to units[0].first)
                    Panel(group) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InputBox(cur.first, true, Modifier.weight(1f)) { t -> app.units[group] = t to (app.units[group]?.second ?: units[0].first); app.touch() }
                            SelectBox(cur.second, units.map { com.calcs.app.core.Opt(it.first, it.first) }, Modifier.weight(1f)) { u -> app.units[group] = (app.units[group]?.first ?: "1") to u; app.touch() }
                        }
                        Spacer(Modifier.height(10.dp))
                        val x = parseNum(cur.first)
                        val from = units.firstOrNull { it.first == cur.second }?.second ?: 1.0
                        ResultList(units.map { (u, f) ->
                            val y = x * from / f
                            val a = kotlin.math.abs(y)
                            com.calcs.app.core.RRow(u, if (x.isNaN()) "—" else y, "", if (a == 0.0) 0 else if (a >= 1000) 1 else if (a >= 1) 3 else 5, u == cur.second)
                        })
                        if (v < 0) Text("")
                    }
                }
            }
        }
    }
}
