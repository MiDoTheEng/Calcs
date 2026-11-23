@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package com.calcs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.Eng
import com.calcs.app.core.Res
import com.calcs.app.core.ToolState
import com.calcs.app.core.fmt

/** Fire branch sketch: riser on the left, most remote sprinkler (S1) on the right. Tap a sprinkler for its data. */
@Composable
fun BranchSketch(s: ToolState, r: Res, avail: Dp) {
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    val rows = r.rows
    val n = rows.size
    var sel by remember { mutableIntStateOf(-1) }
    if (sel >= n || sel < 0) sel = n - 1
    val lim = r["lim"] as? DoubleArray ?: doubleArrayOf(3.0, 6.0, 10.0)
    fun vcol(v: Double) = if (v > lim[1]) cc.vBad else if (v > lim[0]) cc.vOk else cc.vGood
    val gap = 104.dp
    val wNeeded = gap * (n + 1) + 120.dp
    val canvasW = maxOf(wNeeded, avail - 32.dp)
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current

    Panel("Branch sketch", "Tap a sprinkler to see its data next to the one before it. Pipe colour = velocity.") {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier.width(canvasW).height(240.dp).pointerInput(n, canvasW) {
                    detectTapGestures { pos ->
                        val gapPx = gap.toPx(); val right = size.width - 60.dp.toPx()
                        var best = -1; var bd = Float.MAX_VALUE
                        for (i in 0 until n) { val x = right - i * gapPx; val d = kotlin.math.abs(pos.x - x); if (d < bd) { bd = d; best = i } }
                        if (best >= 0 && bd < gapPx / 2) sel = best
                    }
                }
            ) {
                val gapPx = gap.toPx(); val right = size.width - 60.dp.toPx(); val y0 = 128.dp.toPx()
                fun x(i: Int) = right - i * gapPx
                val riserX = x(n - 1) - gapPx
                drawRoundRect(cc.head, Offset(riserX - 7.dp.toPx(), 30.dp.toPx()), Size(14.dp.toPx(), 170.dp.toPx()), CornerRadius(4.dp.toPx()))
                drawText(tm, "Riser", Offset(riserX - 18.dp.toPx(), 8.dp.toPx()), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cc.ink))
                drawText(tm, "Inlet ${fmt(r["Pin"], 2)} bar · ${fmt(r["Q"], 0)} L/min", Offset(riserX + 14.dp.toPx(), 206.dp.toPx()),
                    TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cc.ink))
                rows.forEachIndexed { i, o ->
                    val v = o["v"] as Double; val d = o["d"] as Double
                    val xa = x(i); val xb = if (i == n - 1) riserX else x(i + 1)
                    val wpx = ((d - 15) / 5).coerceIn(3.0, 11.0).toFloat() * density.density
                    drawLine(vcol(v), Offset(xa, y0), Offset(xb, y0), strokeWidth = wpx, cap = StrokeCap.Round)
                    drawText(tm, "${fmt(v, 1)} m/s", Offset((xa + xb) / 2 - 26.dp.toPx(), y0 + 12.dp.toPx()), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = vcol(v)))
                    drawText(tm, "Ø${fmt(d, 1)}", Offset((xa + xb) / 2 - 18.dp.toPx(), y0 + 30.dp.toPx()), TextStyle(fontSize = 11.sp, color = cc.muted))
                }
                rows.forEachIndexed { i, o ->
                    val xa = x(i); val on = i == sel
                    drawLine(cc.muted, Offset(xa, y0 - 18.dp.toPx()), Offset(xa, y0), strokeWidth = 2.dp.toPx())
                    val rad = (if (on) 10 else 8).dp.toPx()
                    drawCircle(if (on) accent else cc.surface, rad, Offset(xa, y0 - 24.dp.toPx()))
                    drawCircle(accent, rad, Offset(xa, y0 - 24.dp.toPx()), style = Stroke(3.dp.toPx()))
                    drawText(tm, "S${i + 1}", Offset(xa - 10.dp.toPx(), 24.dp.toPx()), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = cc.ink))
                    drawText(tm, "${fmt(o["P"], 2)} bar", Offset(xa - 28.dp.toPx(), 42.dp.toPx()), TextStyle(fontSize = 12.sp, color = cc.ink))
                    drawText(tm, "${fmt(o["q"], 1)} L/min", Offset(xa - 30.dp.toPx(), 58.dp.toPx()), TextStyle(fontSize = 11.sp, color = cc.muted))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(cc.vGood, "< ${fmt(lim[0], 0)} m/s"); Legend(cc.vOk, "${fmt(lim[0], 0)}–${fmt(lim[1], 0)} m/s"); Legend(cc.vBad, "> ${fmt(lim[1], 0)} m/s → upsize")
        }
        Spacer(Modifier.height(10.dp))
        val a = rows.getOrNull(sel); val p = rows.getOrNull(sel - 1)
        if (a != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("S${sel + 1} (selected)", listOf("P = ${fmt(a["P"], 3)} bar (${fmt((a["P"] as Double) * Eng.BAR_M, 1)} m)", "q = ${fmt(a["q"], 1)} L/min",
                "Pipe after it: ${fmt(a["Q"], 1)} L/min · Ø${fmt(a["d"], 1)} · ${fmt(a["v"], 2)} m/s", "Loss to next point: ${fmt(a["loss"], 3)} bar"))
            if (p != null) {
                InfoBox("S$sel (before it)", listOf("P = ${fmt(p["P"], 3)} bar", "q = ${fmt(p["q"], 1)} L/min", "Pipe after it: ${fmt(p["Q"], 1)} L/min · ${fmt(p["v"], 2)} m/s"))
                InfoBox("Difference S${sel + 1} − S$sel", listOf("ΔP = +${fmt((a["P"] as Double) - (p["P"] as Double), 3)} bar", "Δq = +${fmt((a["q"] as Double) - (p["q"] as Double), 1)} L/min",
                    "Pressure rises toward the riser, so each sprinkler gives more water."))
            } else InfoBox("S1 is the most remote", listOf("Its pressure is your input (${s.s("p1")} bar).", "Every other sprinkler is worked out from it."))
        }
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 18.dp, height = 5.dp).padding(0.dp)) { Canvas(Modifier.fillMaxWidth().height(5.dp)) { drawRoundRect(c, cornerRadius = CornerRadius(3f)) } }
        Spacer(Modifier.width(6.dp)); SmallLabel(t)
    }
}

@Composable
private fun InfoBox(title: String, lines: List<String>) {
    val cc = LocalCalc.current
    Surface(shape = RoundedCornerShape(12.dp), color = cc.surface2, border = androidx.compose.foundation.BorderStroke(1.dp, cc.line), modifier = Modifier.widthIn(min = 220.dp, max = 360.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = cc.ink)
            lines.forEach { Text(it, fontSize = 14.sp, color = cc.ink, style = T.num) }
        }
    }
}
