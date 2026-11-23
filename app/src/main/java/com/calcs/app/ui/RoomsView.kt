@file:OptIn(ExperimentalLayoutApi::class)

package com.calcs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.RowData
import com.calcs.app.core.Rooms
import com.calcs.app.core.ToolState
import com.calcs.app.core.fmt
import com.calcs.app.core.n0
import com.calcs.app.core.parseNum
import com.calcs.app.core.toText

private fun base(n: String) = n.split(",", "(", " - ").first().trim()

/** Fixtures entered room by room: rooms of one apartment (× apartments) + rooms outside the apartments. */
@Composable
fun RoomsView(b: Rooms, s: ToolState, app: AppState, avail: Dp) {
    val cc = LocalCalc.current
    @Suppress("UNUSED_VARIABLE") val ver = app.version
    val rooms = s.list(b.key)
    val units = s.n0("units").let { if (it > 0) it else 1.0 }
    Panel(b.title, b.help) {
        val inner = avail - 64.dp
        val cols = if (inner < 600.dp) 1 else if (inner < 960.dp) 2 else 3
        val cardW = (inner - 12.dp * (cols - 1)) / cols - 1.dp
        val groups = if (b.perUnit) listOf("apt" to "Inside each apartment  (× ${fmt(units, 0)} apartments)", "common" to "Outside the apartments  (lobby, gym, roof…) – counted once")
            else listOf("" to "")
        groups.forEachIndexed { gi, (where, title) ->
            if (gi > 0) Spacer(Modifier.height(18.dp))
            if (title.isNotEmpty()) { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = cc.ink); Spacer(Modifier.height(8.dp)) }
            val mine = rooms.withIndex().filter { !b.perUnit || (it.value["where"] ?: "apt") == where }
            if (mine.isEmpty()) Text(if (where == "common") "None – add public toilets, gym, etc. if the building has them." else "No rooms yet – add them below.", style = T.small, color = cc.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mine.forEach { (i, r) -> RoomCard(b, rooms, i, r, app, cardW, where) }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.templates.filter { !b.perUnit || it.common == (where == "common") }.forEach { tp ->
                    OutlinedButton(onClick = { rooms.add(Rooms.room(tp).also { if (!b.perUnit) it["where"] = "apt" }); app.touch() }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tp.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(b: Rooms, rooms: MutableList<RowData>, i: Int, r: RowData, app: AppState, w: Dp, where: String) {
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    @Suppress("UNUSED_VARIABLE") val ver = app.version   // redraw after every change (the room map is not observable)
    val fx = Rooms.fx(r)
    var add by remember { mutableStateOf(false) }
    Column(Modifier.width(w).clip(RoundedCornerShape(14.dp)).background(cc.surface2).border(1.dp, cc.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FixtureIcon(r["icon"] ?: "sink", 30.dp, accent)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { InputBox(r["name"] ?: "", false, Modifier.fillMaxWidth()) { v -> r["name"] = v; app.touch() } }
            IconButton(onClick = { rooms.removeAt(i); app.reload() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Delete, "Delete room", tint = cc.muted) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (!b.perUnit) "How many" else if (where == "common") "How many in the building" else "How many per apartment", style = T.small, color = cc.muted, modifier = Modifier.weight(1f))
            val n = r.n0("n")
            Step("−") { r["n"] = toText(maxOf(0.0, n - 1)); app.touch() }
            NumBox(fieldId(r, "n"), "${r["name"]} – how many", r["n"] ?: "", Modifier.width(70.dp).padding(horizontal = 6.dp), get = { r["n"] ?: "" }) { v -> r["n"] = v; app.touch() }
            Step("+") { r["n"] = toText(n + 1); app.touch() }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = cc.line)
        fx.forEachIndexed { k, (idx, q) ->
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                FixtureIcon(b.icons.getOrNull(idx) ?: "sink", 24.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val lab = b.labels.getOrNull(idx) ?: listOf("?", "")
                    Text(base(lab[0]), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val variants = b.labels.indices.filter { base(b.labels[it][0]) == base(lab[0]) }
                    val vt = (listOf(lab[0].removePrefix(base(lab[0])).trim(' ', ',', '-', '(', ')')) + lab.getOrElse(1) { "" }.split("·").map { it.trim() })
                        .filter { it.isNotEmpty() && it != "-" }.joinToString(" · ").replaceFirstChar { it.uppercase() }
                    if (variants.size > 1) Box {
                        Text("${vt.ifEmpty { "type" }} ▾", fontSize = 11.5.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { menu = true }.padding(vertical = 2.dp))
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            variants.forEach { j ->
                                DropdownMenuItem(text = { Text(b.labels[j].joinToString(" · ").replace(" · -", ""), fontWeight = if (j == idx) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { Rooms.setFx(r, fx.mapIndexed { m, p -> if (m == k) j to p.second else p }); menu = false; app.touch() })
                            }
                        }
                    } else if (vt.isNotEmpty()) Text(vt, fontSize = 11.sp, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Step("−", 34.dp) {
                    Rooms.setFx(r, if (q <= 1) fx.filterIndexed { m, _ -> m != k } else fx.mapIndexed { m, p -> if (m == k) p.first to q - 1 else p }); app.touch()
                }
                Text(toText(q), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cc.ink, style = T.num, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Step("+", 34.dp) { Rooms.setFx(r, fx.mapIndexed { m, p -> if (m == k) p.first to q + 1 else p }); app.touch() }
            }
        }
        Box {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { add = true }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = accent); Spacer(Modifier.width(4.dp))
                Text("Add fixture", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            DropdownMenu(expanded = add, onDismissRequest = { add = false }) {
                b.labels.indices.forEach { j ->
                    DropdownMenuItem(
                        leadingIcon = { FixtureIcon(b.icons.getOrNull(j) ?: "sink", 22.dp) },
                        text = { Column { Text(base(b.labels[j][0]), fontWeight = FontWeight.SemiBold); val v = b.labels[j].joinToString(" · ").removePrefix(base(b.labels[j][0])).trim(' ', ',', '·', '-'); if (v.isNotEmpty()) Text(v, fontSize = 11.sp, color = cc.muted) } },
                        onClick = {
                            val ex = fx.indexOfFirst { it.first == j }
                            Rooms.setFx(r, if (ex >= 0) fx.mapIndexed { m, p -> if (m == ex) p.first to p.second + 1 else p } else fx + (j to 1.0))
                            add = false; app.touch()
                        })
                }
            }
        }
    }
}

@Composable
private fun Step(t: String, size: Dp = 40.dp, onClick: () -> Unit) {
    val cc = LocalCalc.current
    Box(Modifier.size(size).clip(CircleShape).background(cc.surface).border(1.dp, cc.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Suppress("unused") private fun unused(x: String?) = parseNum(x)
