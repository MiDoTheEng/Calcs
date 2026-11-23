@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.calcs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.FixedCol
import com.calcs.app.core.Grid
import com.calcs.app.core.Kind
import com.calcs.app.core.RRow
import com.calcs.app.core.Reports
import com.calcs.app.core.Res
import com.calcs.app.core.RowData
import com.calcs.app.core.ToolState
import com.calcs.app.core.fmt
import com.calcs.app.core.parseNum

private fun fixedW(i: Int, c: FixedCol): Dp = if (i == 0) 210.dp else if (c.dec >= 0) 92.dp else 120.dp
private fun colW(f: com.calcs.app.core.Field): Dp = if (f.width > 0) f.width.dp else when (f.kind) { Kind.TEXT -> 180.dp; Kind.SELECT -> 140.dp; Kind.NUM -> 104.dp }
private fun outW(o: com.calcs.app.core.Out): Dp = if (o.label.contains("check", true) || o.label == "Duration" || o.label == "Size (mm)" || o.label == "Rate") 170.dp else 104.dp

/** Make sure the list has the rows the grid needs. */
fun ensureRows(b: Grid, s: ToolState) {
    val list = s.list(b.key)
    val n = Reports.rowCount(b, s)
    while (list.size < n) list.add(if (list.isEmpty()) linkedMapOf() else LinkedHashMap(list.last()))
}

@Composable
fun GridView(b: Grid, s: ToolState, r: Res, app: AppState, avail: Dp) {
    val cc = LocalCalc.current
    ensureRows(b, s)
    val list = s.list(b.key)
    val n = Reports.rowCount(b, s)
    val widths = buildList {
        if (b.rowLabel != null) add(56.dp)
        b.fixedCols.forEachIndexed { i, c -> add(fixedW(i, c)) }
        b.cols.forEach { add(colW(it)) }
        b.outs.forEach { add(outW(it)) }
        if (b.add != null) add(52.dp)
    }
    var total = 0.dp; widths.forEach { total += it }
    val inner = avail - 32.dp
    val tableMode = inner >= total
    var onlyUsed by remember { mutableStateOf(false) }

    Panel(b.title, b.help) {
        if (b.skipEmpty && !tableMode) {
            FilterChip(selected = onlyUsed, onClick = { onlyUsed = !onlyUsed }, label = { Text("Show only rows with quantities") })
            Spacer(Modifier.height(8.dp))
        }
        if (tableMode) {
            val scale = minOf(inner / total, 1.45f)
            Column(Modifier.width(total * scale).clip(RoundedCornerShape(12.dp)).border(1.dp, cc.line, RoundedCornerShape(12.dp))) {
                // header
                Row(Modifier.width(total * scale).background(cc.head).heightIn(min = 44.dp), verticalAlignment = Alignment.Bottom) {
                    val heads = Reports.gridHead(b) + if (b.add != null) listOf("") else emptyList()
                    heads.forEachIndexed { j, h ->
                        Text(h, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp,
                            textAlign = if (j >= heads.size - b.outs.size - (if (b.add != null) 1 else 0) && j < heads.size - (if (b.add != null) 1 else 0)) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.width(widths[j] * scale).padding(horizontal = 8.dp, vertical = 8.dp))
                    }
                }
                for (i in 0 until n) {
                    val row = list[i]
                    HorizontalDivider(color = cc.line)
                    Row(Modifier.width(total * scale).background(if (i % 2 == 1) cc.surface2 else cc.surface).heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                        var j = 0
                        b.rowLabel?.let { Text(it(i), fontWeight = FontWeight.Bold, color = cc.ink, modifier = Modifier.width(widths[j++] * scale).padding(horizontal = 8.dp)) }
                        b.fixed?.get(i)?.forEachIndexed { k, v ->
                            val c = b.fixedCols[k]
                            Text(fmt(v, c.dec), color = if (k == 0) cc.ink else cc.muted, fontSize = 14.sp, fontWeight = if (k == 0 && rowUsed(b, row)) FontWeight.Bold else FontWeight.Normal,
                                textAlign = if (v is Number) TextAlign.End else TextAlign.Start, style = T.num, modifier = Modifier.width(widths[j++] * scale).padding(horizontal = 8.dp))
                        }
                        b.cols.forEach { f -> BoundInput(f, row, app, Modifier.width(widths[j++] * scale).padding(horizontal = 4.dp), onStructural = { app.reload() }) }
                        b.outs.forEach { o ->
                            val v = o.get(r, i)
                            Box(Modifier.width(widths[j++] * scale).heightIn(min = 54.dp).background(if (o.key) cc.keyBg else cc.resBg).padding(horizontal = 8.dp), contentAlignment = if (v is String) Alignment.CenterStart else Alignment.CenterEnd) {
                                Text(fmt(v, o.dec), style = T.num, fontSize = 14.sp, fontWeight = if (o.key) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = statusColor(v, if (o.key) cc.keyInk else cc.ink))
                            }
                        }
                        if (b.add != null) DeleteBtn(list, i, app, b.minRows)
                    }
                }
                b.totals?.let { tf ->
                    val map = tf(r).toMap()
                    HorizontalDivider(color = cc.line)
                    Row(Modifier.width(total * scale).background(cc.surface2).heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                        var j = 0
                        val lead = (if (b.rowLabel != null) 1 else 0) + b.fixedCols.size + b.cols.size
                        var leadW = 0.dp; repeat(lead) { leadW += widths[j++] * scale }
                        Text("TOTAL", fontWeight = FontWeight.ExtraBold, color = cc.ink, modifier = Modifier.width(leadW).padding(horizontal = 8.dp))
                        b.outs.forEach { o ->
                            Text(if (map.containsKey(o.label)) fmt(map[o.label], o.dec) else "", style = T.num, fontWeight = FontWeight.ExtraBold, color = cc.ink, textAlign = TextAlign.End,
                                modifier = Modifier.width(widths[j++] * scale).padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        } else {
            // card mode: one card per row, inputs and results wrap to the screen width
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until n) {
                    val row = list[i]
                    if (onlyUsed && b.fixed != null && !rowUsed(b, row)) continue
                    Surface(shape = RoundedCornerShape(14.dp), color = cc.surface2, border = androidx.compose.foundation.BorderStroke(1.dp, cc.line)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val title = when {
                                        b.rowLabel != null -> b.rowLabel.invoke(i)
                                        b.fixed != null -> fmt(b.fixed[i][0], -1)
                                        b.titleKey != null -> row[b.titleKey].orEmpty().ifBlank { "Row ${i + 1}" }
                                        else -> "Row ${i + 1}"
                                    }
                                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cc.ink)
                                    b.fixed?.get(i)?.drop(1)?.let { rest ->
                                        if (rest.isNotEmpty()) SmallLabel(rest.mapIndexed { k, v -> val c = b.fixedCols[k + 1]; if (v is Number) "${c.label}: ${fmt(v, c.dec)}" else v.toString() }.joinToString("  ·  "))
                                    }
                                }
                                if (b.add != null) DeleteBtn(list, i, app, b.minRows)
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                b.cols.forEach { f ->
                                    val w = if (f.kind == Kind.TEXT) maxOf(colW(f), 200.dp) else maxOf(colW(f), 130.dp)
                                    Column(Modifier.width(w)) {
                                        SmallLabel(if (f.unit.isNotEmpty()) "${f.label} (${f.unit})" else f.label)
                                        Spacer(Modifier.height(3.dp))
                                        BoundInput(f, row, app, Modifier.fillMaxWidth(), onStructural = { app.reload() })
                                    }
                                }
                            }
                            if (b.outs.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    b.outs.forEach { o ->
                                        val v = o.get(r, i)
                                        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(if (o.key) cc.keyBg else cc.resBg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            SmallLabel(o.label)
                                            Text(fmt(v, o.dec).ifEmpty { "—" }, style = T.num, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = statusColor(v, if (o.key) cc.keyInk else cc.ink))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                b.totals?.let { tf ->
                    val rows = tf(r).map { (k, v) -> RRow(k, v, "", b.outs.firstOrNull { it.label == k }?.dec ?: 1, true) }
                    if (rows.isNotEmpty()) ResultList(rows.map { RRow("Total – " + it.label, it.value, it.unit, it.dec, true) })
                }
            }
        }
        if (b.add != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                val nr: RowData = b.add.invoke()
                nr["name"]?.let { nr["name"] = "$it ${list.size + 1}" }
                nr["zone"]?.let { nr["zone"] = "$it ${list.size + 1}" }
                list.add(nr); app.reload()
            }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add row") }
        }
        b.results?.let { rf ->
            Spacer(Modifier.height(14.dp))
            ResultList(rf(s, r))
        }
    }
}

private fun rowUsed(b: Grid, row: RowData) = b.cols.any { parseNum(row[it.k]) > 0 }

@Composable
private fun DeleteBtn(list: MutableList<RowData>, i: Int, app: AppState, minRows: Int) {
    val cc = LocalCalc.current
    IconButton(onClick = { if (list.size > minRows) { list.removeAt(i); app.reload() } }, modifier = Modifier.size(44.dp)) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete row", tint = if (list.size > minRows) cc.muted else cc.line)
    }
}
