@file:OptIn(ExperimentalLayoutApi::class)

package com.calcs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.Field
import com.calcs.app.core.Kind
import com.calcs.app.core.Opt
import com.calcs.app.core.RRow
import com.calcs.app.core.Status
import com.calcs.app.core.fmt
import com.calcs.app.core.parseNum
import com.calcs.app.core.statusOf

/* ------------------------------- inputs ------------------------------- */

@Composable
fun InputBox(initial: String, numeric: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val cc = LocalCalc.current
    var tf by remember { mutableStateOf(TextFieldValue(initial)) }
    val source = remember { MutableInteractionSource() }
    val kp = LocalKeypad.current
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = tf,
        onValueChange = { v -> val changed = v.text != tf.text; tf = v; if (changed) onChange(v.text) },
        singleLine = true,
        interactionSource = source,
        textStyle = TextStyle(fontSize = 16.sp, color = cc.ink, textAlign = if (numeric) TextAlign.End else TextAlign.Start, fontFeatureSettings = "tnum"),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cc.inBg)
            .border(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else cc.inLine, RoundedCornerShape(10.dp))
            .onFocusChanged { if (it.isFocused) { kp?.close(); if (numeric) tf = tf.copy(selection = TextRange(0, tf.text.length)) } },
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) { inner() }
        }
    )
}

@Composable
fun SelectBox(value: String, options: List<Opt>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val cc = LocalCalc.current
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.value == value || (!parseNum(value).isNaN() && parseNum(it.value) == parseNum(value)) }?.label ?: value
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(cc.inBg)
                .border(1.dp, cc.inLine, RoundedCornerShape(10.dp)).clickable { open = true }.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cc.ink)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = cc.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.label, fontWeight = if (o.label == label) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onSelect(o.value) })
            }
        }
    }
}

/** Same id for a field everywhere (number box, diagram label) so they light up together. */
fun fieldId(map: MutableMap<String, String>, k: String) = "${System.identityHashCode(map)}:$k"

/** Keypad target for a field, used by diagram labels. */
fun fieldTarget(f: Field, map: MutableMap<String, String>, app: AppState, onStructural: () -> Unit) =
    NumTarget(fieldId(map, f.k), if (f.unit.isNotEmpty()) "${f.label} (${f.unit})" else f.label, { map[f.k] ?: "" },
        { v -> map[f.k] = v; app.touch() }, { if (f.structural) onStructural() })

/** An input bound to a key of a map. */
@Composable
fun BoundInput(f: Field, map: MutableMap<String, String>, app: AppState, modifier: Modifier = Modifier, onStructural: () -> Unit = {}) {
    when (f.kind) {
        Kind.SELECT -> SelectBox(map[f.k] ?: "", f.options, modifier) { v ->
            map[f.k] = v
            if (f.onChange != null) { f.onChange.invoke(com.calcs.app.core.ToolState(map)); app.reload() } else app.touch()
        }
        Kind.NUM -> NumBox(fieldId(map, f.k), if (f.unit.isNotEmpty()) "${f.label} (${f.unit})" else f.label, map[f.k] ?: "", modifier,
            commit = { if (f.structural) onStructural() }, get = { map[f.k] ?: "" }) { v -> map[f.k] = v; app.touch() }
        Kind.TEXT -> InputBox(map[f.k] ?: "", false, modifier) { v -> map[f.k] = v; app.touch() }
    }
}

/* ------------------------------- containers ------------------------------- */

@Composable
fun Panel(title: String?, help: String = "", modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cc = LocalCalc.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cc.surface
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) Text(title, style = T.section, color = MaterialTheme.colorScheme.primary)
            if (help.isNotEmpty()) Text(help, style = T.small, color = cc.muted, modifier = Modifier.padding(top = 4.dp))
            if (title != null || help.isNotEmpty()) Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun FieldRow(f: Field, map: MutableMap<String, String>, app: AppState, narrow: Boolean, onStructural: () -> Unit = {}) {
    val cc = LocalCalc.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (f.kind == Kind.SELECT && f.unit.isEmpty() && narrow) {
            Text(f.label, style = T.label, color = cc.ink)
            Spacer(Modifier.height(6.dp))
            BoundInput(f, map, app, Modifier.fillMaxWidth(), onStructural)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, style = T.label, color = cc.ink, modifier = Modifier.weight(1f).padding(end = 10.dp))
                val w: Dp = if (f.kind == Kind.SELECT) (if (narrow) 170.dp else 240.dp) else if (narrow) 112.dp else 140.dp
                BoundInput(f, map, app, Modifier.width(w), onStructural)
                if (!(f.kind == Kind.SELECT && f.unit.isEmpty())) Text(f.unit, style = T.small, color = cc.muted, modifier = Modifier.width(if (narrow) 64.dp else 84.dp).padding(start = 8.dp))
            }
        }
        if (f.hint.isNotEmpty()) Text(f.hint, style = T.small, color = cc.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

/* ------------------------------- results ------------------------------- */

@Composable
fun statusColor(v: Any?, base: Color): Color {
    val cc = LocalCalc.current
    return when (statusOf(v)) { Status.OK -> cc.ok; Status.BAD -> cc.bad; else -> base }
}

@Composable
fun ResultList(rows: List<RRow>) {
    val cc = LocalCalc.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, cc.line, RoundedCornerShape(12.dp))) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = cc.line)
            Row(
                Modifier.fillMaxWidth().background(if (r.key) cc.keyBg else cc.resBg).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.label, style = T.label, color = if (r.key) cc.keyInk else cc.ink, fontWeight = if (r.key) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(fmt(r.value, r.dec), style = T.num.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                    color = statusColor(r.value, if (r.key) cc.keyInk else cc.ink), modifier = Modifier.widthIn(max = 260.dp))
                Text(r.unit, style = T.small, color = cc.muted, modifier = Modifier.width(58.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun Tiles(rows: List<RRow>, avail: Dp) {
    val cc = LocalCalc.current
    if (rows.isEmpty()) return
    val gap = 10.dp
    val cols = if (avail < 600.dp) 2 else minOf(rows.size, maxOf(1, ((avail + gap) / (190.dp + gap)).toInt()))
    val tileW = (avail - gap * (cols - 1)) / cols - 1.dp
    val big = avail >= 600.dp
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { r ->
            Surface(shape = RoundedCornerShape(14.dp), color = cc.surface, border = androidx.compose.foundation.BorderStroke(1.dp, cc.line), modifier = Modifier.width(tileW)) {
                Column {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(r.label, style = T.small, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmt(r.value, r.dec), fontSize = if (r.value is String && (r.value as String).length > 9) (if (big) 17.sp else 15.sp) else if (big) 23.sp else 19.sp, fontWeight = FontWeight.ExtraBold, style = T.num, color = statusColor(r.value, cc.ink),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (r.unit.isNotEmpty()) Text(" " + r.unit, style = T.small, color = cc.muted, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = T.small, color = LocalCalc.current.muted, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun MinHeight(content: @Composable () -> Unit) { Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.CenterStart) { content() } }
