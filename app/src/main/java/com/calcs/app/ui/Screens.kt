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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.CompositionLocalProvider
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
                saved = withContext(Dispatchers.Default) {
                    val files = jobs.map { bs -> val (name, bytes) = Exporter.build(ctx, app, bs, pdf); Exporter.save(ctx, name, bytes, app.project.name) }
                    try { Exporter.saveBackup(ctx, app) } catch (_: Exception) { }
                    files
                }
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
            title = { Text(if (list.size == 1) "Saved" else "${list.size} files saved") },
            text = { Column { Text("Folder: ${list[0].folder}", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); list.forEach { Text(it.name, style = T.small) } } },
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var backupSaved by remember { mutableStateOf<Saved?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null }
            val name = text?.let { app.importBackup(it) }
            msg = if (name != null) "Opened “$name”. The project you had open is kept in Old projects." else "That file is not a Calcs backup (.calcs)."
        }
    }
    fun backup() {
        scope.launch {
            try { backupSaved = withContext(Dispatchers.IO) { app.saveNow(); Exporter.saveBackup(ctx, app) } }
            catch (e: Exception) { msg = "Could not save the backup: ${e.message}" }
        }
    }
    Scaffold(
        containerColor = cc.bg,
        topBar = {
            TopAppBar(
                title = { Column { Text("Calcs", fontWeight = FontWeight.ExtraBold); Text("MEP design calculations · v" + appVersion(), style = T.small, color = cc.muted) } },
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel("Project details (printed on every report)") {
                    key(app.generation) { ProjectDetails(app) }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ask = "new" }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New project") }
                        OutlinedButton(onClick = { ask = "old" }) { Text("Old projects") }
                        OutlinedButton(onClick = { backup() }) { Text("Back up / share project") }
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Open backup file") }
                        OutlinedButton(onClick = { ask = "examples" }) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load worked examples") }
                    }
                }
            }
            Catalog.BRANCHES.forEach { b -> item { BranchCard(b.short, b.title, b.desc, b.tools.map { it.title }, accentFor(b.accent, false)) { app.screen = Screen.BranchScr(b.id) } } }
            item { BranchCard("U", "Unit converter", "Flow, pressure, length, volume, area, power, velocity, sprinkler density & K-factor", emptyList(), Color(0xFF5B5BD6)) { app.screen = Screen.Units } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Educational tool – verify every value against the governing code edition and the authority requirements.", style = T.small, color = cc.muted, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
    msg?.let { m -> AlertDialog(onDismissRequest = { msg = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { msg = null }) { Text("OK") } }) }
    backupSaved?.let { b ->
        AlertDialog(onDismissRequest = { backupSaved = null }, title = { Text("Backup saved") },
            text = { Text("${b.name}\nin ${b.folder}\n\nOpen this file on any device with Calcs installed (Open backup file) to get the whole project back. Share it to Drive or WhatsApp to keep a copy.") },
            confirmButton = { TextButton(onClick = { backupSaved = null; Exporter.share(ctx, b) }) { Text("Share") } },
            dismissButton = { TextButton(onClick = { backupSaved = null }) { Text("Done") } })
    }
    when (ask) {
        "new" -> Confirm("Start a new project?", "“${app.project.name.ifBlank { "Untitled project" }}” is kept in Old projects – you can open it again any time.", "Start new project", { app.newProject() }) { ask = null }
        "old" -> OldProjects(app) { ask = null }
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
    val kp = remember { Keypad() }
    BackHandler(enabled = kp.active != null) { kp.close() }
    // tab 0 = system overview (schematic); tab i = calculation i-1. Every system opens on its overview.
    var tab by remember { mutableStateOf(0) }
    val tool: ToolDef? = if (tab == 0) null else b.tools[tab - 1]
    CompositionLocalProvider(LocalKeypad provides kp) {
    Scaffold(
        containerColor = cc.bg,
        bottomBar = { KeypadBar(kp) },
        topBar = {
            Column(Modifier.background(cc.surface)) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { app.saveNow(); app.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Column { Text(b.title, fontWeight = FontWeight.Bold); Text(app.project.name.ifBlank { "Untitled project" }, style = T.small, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    actions = { ExportActions(app, listOf(b), tool) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cc.surface)
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = cc.surface) {
                    (listOf("Overview") + b.tools.map { it.title }).forEachIndexed { i, title ->
                        Tab(selected = i == tab, onClick = { kp.close(); tab = i },
                            text = { Text(title, fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal) })
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()).fillMaxSize()) {
            if (tool == null) {
                BoxWithConstraints(Modifier.fillMaxSize()) { OverviewContent(b, minOf(maxWidth, 1240.dp)) { t -> tab = t + 1 } }
            } else key(tool.id) { ToolContent(app, tool) }
        }
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
                        if (blk.picker != null && blk.fixed != null) item(key = "g$bi-p", contentType = "grid-p") {
                            Part(PartPos.MID) { Column(Modifier.padding(bottom = 14.dp)) { FixturePicker(blk, list, app, w) } }
                        }
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
        b.diagram?.let { InputDiagramView(it, b.fields, s.f, app) { app.reload() } }
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
    val kp = remember { Keypad() }
    BackHandler(enabled = kp.active != null) { kp.close() }
    CompositionLocalProvider(LocalKeypad provides kp) {
    Scaffold(
        containerColor = cc.bg,
        bottomBar = { KeypadBar(kp) },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 16.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Eng.UNITS.forEach { (group, units) ->
                item {
                    val cur = app.units[group] ?: ("1" to units[0].first)
                    Panel(group) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumBox("units:$group", group, cur.first, Modifier.weight(1f), get = { app.units[group]?.first ?: "1" }) { t -> app.units[group] = t to (app.units[group]?.second ?: units[0].first); app.touch() }
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
}

@Composable
private fun appVersion(): String {
    val ctx = LocalContext.current
    return remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" } }
}

@Composable
private fun OldProjects(app: AppState, onClose: () -> Unit) {
    val cc = LocalCalc.current
    val v = app.archiveVersion
    val list = remember(v) { app.archived() }
    var del by remember { mutableStateOf<AppState.Archived?>(null) }
    val fmtDate = remember { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.US) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Old projects") },
        text = {
            if (list.isEmpty()) Text("No old projects yet. When you start a new project, the one you had open is kept here.")
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list.size) { i ->
                    val a = list[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold, color = cc.ink)
                            Text("Kept " + fmtDate.format(java.util.Date(a.savedAt)), style = T.small, color = cc.muted)
                        }
                        TextButton(onClick = { app.openArchived(a.id); onClose() }) { Text("Open") }
                        TextButton(onClick = { del = a }) { Text("Delete", color = cc.bad) }
                    }
                    if (i < list.size - 1) androidx.compose.material3.HorizontalDivider(color = cc.line)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
    del?.let { a -> Confirm("Delete “${a.name}”?", "This old project will be removed for good. Exported PDF / Excel / backup files are not touched.", "Delete", { app.deleteArchived(a.id) }) { del = null } }
}

/** Project details: typed once and saved; shown as text afterwards, with an Edit button. */
@Composable
private fun ProjectDetails(app: AppState) {
    val cc = LocalCalc.current
    var editing by remember { mutableStateOf(app.project.name.isBlank()) }
    val p = app.project
    if (editing) {
        var name by remember { mutableStateOf(p.name) }
        var loc by remember { mutableStateOf(p.location) }
        var eng by remember { mutableStateOf(p.engineer) }
        var cli by remember { mutableStateOf(p.client) }
        var date by remember { mutableStateOf(p.date) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjField("Project name", name) { name = it }
            ProjField("Location", loc) { loc = it }
            ProjField("Prepared by", eng) { eng = it }
            ProjField("Client / reference", cli) { cli = it }
            ProjField("Date (blank = today)", date) { date = it }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                p.name = name.trim(); p.location = loc.trim(); p.engineer = eng.trim(); p.client = cli.trim(); p.date = date.trim()
                app.saveNow(); editing = false; app.reload()
            }, enabled = name.isNotBlank()) { Text("Save details") }
            if (p.name.isNotBlank()) TextButton(onClick = { editing = false }) { Text("Cancel") }
        }
        if (name.isBlank()) SmallLabel("Type a project name to save.", Modifier.padding(top = 4.dp))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Project" to p.name, "Location" to p.location, "Prepared by" to p.engineer, "Client / reference" to p.client, "Date" to p.date.ifBlank { "today's date on each export" })
                .filter { it.second.isNotBlank() }
                .forEach { (k, v) ->
                    Row {
                        Text(k, style = T.small, color = cc.muted, modifier = Modifier.width(140.dp))
                        Text(v, fontWeight = if (k == "Project") FontWeight.Bold else FontWeight.Normal, color = cc.ink)
                    }
                }
            OutlinedButton(onClick = { editing = true }) { Text("Edit details") }
        }
    }
}
