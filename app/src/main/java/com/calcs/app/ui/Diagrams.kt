@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package com.calcs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.Field
import com.calcs.app.core.Grid
import com.calcs.app.core.Kind
import com.calcs.app.core.RowData
import com.calcs.app.core.fmt
import com.calcs.app.core.parseNum
import com.calcs.app.core.toText

/* ============================== fixture icons ============================== */

@Composable
fun FixtureIcon(kind: String, size: Dp = 36.dp, tint: Color = LocalCalc.current.muted, fill: Color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
    Canvas(Modifier.size(size)) { drawFixture(kind, tint, fill) }
}

private fun DrawScope.drawFixture(kind: String, c: Color, f: Color) {
    val u = size.minDimension / 100f
    val sw = Stroke(width = 5f * u, cap = StrokeCap.Round)
    fun r(x: Float, y: Float, w: Float, h: Float, rad: Float = 6f) { drawRoundRect(f, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(rad * u)); drawRoundRect(c, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(rad * u), style = sw) }
    fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 5f * u, StrokeCap.Round)
    fun o(x: Float, y: Float, w: Float, h: Float) { drawOval(f, Offset(x * u, y * u), Size(w * u, h * u)); drawOval(c, Offset(x * u, y * u), Size(w * u, h * u), style = sw) }
    when (kind) {
        "wc" -> { r(30f, 8f, 40f, 24f, 5f); o(22f, 36f, 56f, 38f); r(40f, 72f, 20f, 18f, 3f) }
        "lav" -> { r(12f, 34f, 76f, 22f, 10f); l(50f, 14f, 50f, 30f); l(50f, 14f, 62f, 14f); r(44f, 58f, 12f, 32f, 3f) }
        "sink" -> { l(6f, 36f, 94f, 36f); r(22f, 40f, 56f, 28f, 4f); l(62f, 36f, 62f, 16f); l(62f, 16f, 46f, 16f); l(46f, 16f, 46f, 24f) }
        "shower" -> { l(22f, 90f, 22f, 12f); l(22f, 12f, 60f, 12f); l(60f, 12f, 60f, 22f); r(48f, 22f, 24f, 8f, 3f)
            for (k in 0 until 4) l(50f + k * 7f, 38f, 48f + k * 7f, 50f); r(10f, 88f, 80f, 6f, 2f) }
        "bath" -> { r(6f, 42f, 88f, 34f, 12f); l(20f, 76f, 16f, 90f); l(80f, 76f, 84f, 90f); l(80f, 42f, 80f, 24f); l(80f, 24f, 68f, 24f) }
        "urinal" -> { r(28f, 8f, 44f, 76f, 18f); o(38f, 40f, 24f, 26f); l(50f, 84f, 50f, 94f) }
        "bidet" -> { o(22f, 44f, 56f, 36f); l(50f, 44f, 50f, 30f); l(50f, 30f, 60f, 30f); r(40f, 80f, 20f, 12f, 3f) }
        "washer" -> { r(16f, 10f, 68f, 82f, 6f); o(32f, 36f, 36f, 36f); l(26f, 22f, 46f, 22f) }
        "dishwasher" -> { r(16f, 10f, 68f, 82f, 6f); l(26f, 24f, 74f, 24f); l(26f, 46f, 74f, 46f); l(26f, 62f, 74f, 62f) }
        "fountain" -> { r(22f, 30f, 56f, 12f, 4f); r(40f, 42f, 20f, 48f, 3f); l(50f, 30f, 50f, 22f); drawArc(c, 200f, 120f, false, Offset(46f * u, 6f * u), Size(28f * u, 28f * u), style = sw) }
        "service" -> { r(14f, 28f, 72f, 44f, 4f); l(50f, 10f, 50f, 26f); l(50f, 10f, 62f, 10f); l(24f, 72f, 24f, 92f); l(76f, 72f, 76f, 92f) }
        "floordrain" -> { r(22f, 22f, 56f, 56f, 6f); l(36f, 30f, 36f, 70f); l(50f, 30f, 50f, 70f); l(64f, 30f, 64f, 70f) }
        "bathgroup" -> { r(6f, 20f, 26f, 16f, 4f); o(4f, 38f, 32f, 24f); r(48f, 38f, 46f, 16f, 8f); l(71f, 24f, 71f, 36f); r(10f, 74f, 80f, 14f, 5f) }
        else -> r(20f, 30f, 60f, 40f)
    }
}

/* ============================== fixture picker ============================== */

/** Icon tiles with − / + for the "per unit" quantity – a quick way to fill a fixture list. */
@Composable
fun FixturePicker(b: Grid, list: List<RowData>, app: AppState, avail: Dp) {
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    val key = b.picker ?: return
    val col = b.cols.firstOrNull { it.k == key } ?: return
    val fixed = b.fixed ?: return
    val inner = avail - 32.dp - 32.dp
    val cols = if (inner < 520.dp) 2 else maxOf(3, (inner / 200.dp).toInt())
    val tileW = (inner - 8.dp * (cols - 1)) / cols - 1.dp
    Text("Tap + / − to add fixtures (${col.label.lowercase()}). The table below shows the totals.", style = T.small, color = cc.muted)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fixed.forEachIndexed { i, rowFixed ->
            val row = list.getOrNull(i) ?: return@forEachIndexed
            val q = parseNum(row[key]).let { if (it.isNaN()) 0.0 else it }
            val on = q > 0
            Column(
                Modifier.width(tileW).clip(RoundedCornerShape(12.dp))
                    .background(if (on) accent.copy(alpha = .10f) else cc.surface2)
                    .border(if (on) 2.dp else 1.dp, if (on) accent else cc.line, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FixtureIcon(b.icon?.invoke(i) ?: "sink", 34.dp, if (on) accent else cc.muted)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rowFixed[0].toString(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                        val sub = rowFixed.drop(1).filter { it is String }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper("−") { row[key] = toText(maxOf(0.0, q - 1)); app.touch() }
                    NumBox(fieldId(row, key), "${rowFixed[0]} – ${col.label}", row[key] ?: "", Modifier.weight(1f).padding(horizontal = 6.dp), get = { row[key] ?: "" }) { v -> row[key] = v; app.touch() }
                    Stepper("+") { row[key] = toText(q + 1); app.touch() }
                }
            }
        }
    }
}

@Composable
private fun Stepper(t: String, onClick: () -> Unit) {
    val cc = LocalCalc.current
    Box(Modifier.size(40.dp).clip(CircleShape).background(cc.surface).border(1.dp, cc.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/* ============================== input diagrams ============================== */

/** A label on a diagram: the field it edits and where it sits (diagram units, 600 × 250). */
class Tag(val k: String, val short: String, val x: Float, val y: Float)
class InputDiagram(val tags: List<Tag>, val draw: Sk.() -> Unit)

private val WATER = Color(0xFF2B7BD8); private val HOT = Color(0xFFD8453A); private val RED = Color(0xFFD23C3C)
private val AIR = Color(0xFF3E9BE0); private val GREEN = Color(0xFF2E9E5B); private val TEAL = Color(0xFF0E9A90); private val PURPLE = Color(0xFF8B6BD6)

private fun Sk.dim(x1: Float, y1: Float, x2: Float, y2: Float) {
    line(x1, y1, x2, y2, cc.muted, 1.5f)
    if (x1 == x2) { arrow(x1, minOf(y1, y2) + 6, 'u', cc.muted, 6f); arrow(x1, maxOf(y1, y2) - 6, 'd', cc.muted, 6f) }
    else { arrow(minOf(x1, x2) + 6, y1, 'l', cc.muted, 6f); arrow(maxOf(x1, x2) - 6, y1, 'r', cc.muted, 6f) }
}
private fun Sk.person(x: Float, y: Float, k: Float = 1f) {
    circle(x, y, 9f * k, null, cc.muted, 2.5f); line(x, y + 9 * k, x, y + 40 * k, cc.muted, 3f)
    line(x - 13 * k, y + 20 * k, x + 13 * k, y + 20 * k, cc.muted, 3f); line(x, y + 40 * k, x - 9 * k, y + 58 * k, cc.muted, 3f); line(x, y + 40 * k, x + 9 * k, y + 58 * k, cc.muted, 3f)
}
private fun Sk.apartments(x: Float, y: Float, w: Float, h: Float, floors: Int, per: Int) {
    rect(x, y, w, h, cc.surface2, cc.muted, 2.5f)
    val fh = h / floors; val uw = w / per
    for (f in 0 until floors) for (u in 0 until per) rect(x + u * uw + 6, y + f * fh + 6, uw - 12, fh - 12, WATER.copy(alpha = .12f), cc.line, 1.5f, 3f)
}

val DIAGRAMS: Map<String, InputDiagram> = mapOf(
    "ws-tank" to InputDiagram(listOf(Tag("units", "Units", 140f, 22f), Tag("persons", "Persons / unit", 300f, 222f), Tag("lpd", "L / person / day", 375f, 30f), Tag("days", "Storage days", 515f, 232f))) {
        apartments(40f, 50f, 200f, 170f, 3, 3)
        person(300f, 120f)
        line(350f, 70f, 395f, 70f, WATER, 5f); line(395f, 70f, 395f, 90f, WATER, 5f); for (k in 0 until 3) circle(395f, 104f + k * 14, 3.5f, WATER)
        tank(455f, 120f, 120f, 90f, WATER, "Tank")
        line(260f, 165f, 440f, 165f, cc.line, 2f); arrow(440f, 165f, 'r', cc.line, 6f)
    },
    "ws-pipe" to InputDiagram(listOf(Tag("id", "Internal Ø", 470f, 22f), Tag("vTarget", "Target velocity", 190f, 50f), Tag("vMax", "Max velocity", 190f, 205f), Tag("C", "Hazen-Williams C", 300f, 236f))) {
        rect(40f, 100f, 330f, 50f, WATER.copy(alpha = .18f), WATER, 3f, 2f)
        arrow(205f, 125f, 'r', WATER, 12f); line(120f, 125f, 195f, 125f, WATER, 4f)
        circle(470f, 125f, 62f, WATER.copy(alpha = .18f), cc.muted, 5f); circle(470f, 125f, 52f, null, WATER, 2f)
        dim(418f, 125f, 522f, 125f)
    },
    "ws-friction" to InputDiagram(listOf(Tag("len", "Straight length", 300f, 236f), Tag("fpct", "Fittings %", 260f, 30f), Tag("feq", "or fittings Le (m)", 470f, 140f))) {
        pump(70f, 175f, WATER)
        poly(85f, 175f, 160f, 175f, 160f, 70f, 380f, 70f, 380f, 150f, 520f, 150f, 520f, 120f, c = WATER, w = 5f)
        listOf(160f to 175f, 160f to 70f, 380f to 70f, 380f to 150f).forEach { (x, y) -> circle(x, y, 7f, cc.surface, cc.muted, 2f) }
        valve(270f, 70f, WATER); fixture(520f, 110f, "lav")
        dim(70f, 205f, 520f, 205f)
    },
    "ws-pump" to InputDiagram(listOf(Tag("stat", "Static height", 520f, 128f), Tag("resBar", "Residual at fixture", 330f, 26f), Tag("safety", "Safety margin", 240f, 140f), Tag("eff", "Pump efficiency", 95f, 150f))) {
        groundLine(20f, 580f, 222f)
        pump(95f, 205f, WATER)
        poly(110f, 205f, 160f, 205f, 160f, 55f, 400f, 55f, 400f, 70f, c = WATER, w = 5f); fixture(400f, 82f, "lav")
        circle(430f, 52f, 11f, cc.surface, cc.muted, 2.5f); line(430f, 52f, 436f, 46f, RED, 2f)
        dim(470f, 205f, 470f, 55f)
    },
    "ws-heater" to InputDiagram(listOf(Tag("tc", "Cold in", 120f, 205f), Tag("ts", "Storage temp", 480f, 48f), Tag("vol", "Volume", 300f, 236f), Tag("rec", "Recovery time", 480f, 175f))) {
        rect(250f, 35f, 100f, 180f, HOT.copy(alpha = .10f), cc.muted, 3f, 18f)
        line(270f, 170f, 330f, 170f, HOT, 3f); line(270f, 185f, 330f, 185f, HOT, 3f)
        poly(60f, 200f, 250f, 200f, c = WATER, w = 5f); arrow(150f, 200f, 'r', WATER, 9f)
        poly(350f, 60f, 560f, 60f, c = HOT, w = 5f); arrow(470f, 60f, 'r', HOT, 9f)
        circle(480f, 140f, 18f, cc.surface, cc.muted, 2.5f); line(480f, 140f, 480f, 128f, cc.muted, 2.5f); line(480f, 140f, 489f, 145f, cc.muted, 2.5f)
    },
    "dr-building" to InputDiagram(listOf(Tag("units", "Identical units", 300f, 24f))) {
        apartments(150f, 50f, 300f, 160f, 3, 4)
        line(470f, 50f, 470f, 232f, Color(0xFF8A5A1E), 6f); line(470f, 232f, 560f, 232f, Color(0xFF8A5A1E), 6f)
    },
    "rn-design" to InputDiagram(listOf(Tag("intensity", "Rain intensity", 300f, 26f), Tag("minLeader", "Min leader size", 470f, 175f))) {
        var x = 120f; while (x < 500f) { line(x, 48f, x - 10, 72f, TEAL.copy(alpha = .6f), 2.5f); x += 36f }
        line(80f, 110f, 520f, 110f, cc.muted, 6f); line(80f, 88f, 80f, 110f, cc.muted, 6f); line(520f, 88f, 520f, 110f, cc.muted, 6f)
        line(380f, 110f, 380f, 232f, TEAL, 6f); arrow(380f, 180f, 'd', TEAL, 9f)
        groundLine(40f, 560f, 232f)
    },
    "ff-sprinkler" to InputDiagram(listOf(Tag("coverage", "Area per sprinkler", 300f, 232f), Tag("K", "K-factor", 440f, 40f), Tag("pMin", "Min pressure", 160f, 40f))) {
        line(60f, 40f, 540f, 40f, cc.muted, 4f); line(300f, 40f, 300f, 58f, RED, 4f); line(288f, 60f, 312f, 60f, RED, 4f)
        val p = Path(); p.moveTo(300f * s, 62f * s); p.lineTo(170f * s, 200f * s); p.lineTo(430f * s, 200f * s); p.close()
        ds.drawPath(p, RED.copy(alpha = .10f))
        val q = Path(); q.moveTo(150f * s, 200f * s); q.lineTo(450f * s, 200f * s); q.lineTo(410f * s, 175f * s); q.lineTo(190f * s, 175f * s); q.close()
        ds.drawPath(q, RED.copy(alpha = .18f)); ds.drawPath(q, RED, style = Stroke(2.5f * s))
    },
    "ff-riser" to InputDiagram(listOf(Tag("risers", "No. of risers", 300f, 120f), Tag("lvBar", "Residual at LV", 400f, 70f), Tag("lvElev", "LV elevation", 205f, 140f),
        Tag("lvFric", "LV friction", 120f, 120f), Tag("spElev", "Sprinkler elev.", 560f, 140f), Tag("spFric", "Sprinkler friction", 470f, 200f))) {
        groundLine(20f, 580f, 222f)
        rect(260f, 40f, 260f, 182f, null, cc.line, 2.5f, 2f); listOf(100f, 160f).forEach { line(260f, it, 520f, it, cc.line, 2f) }
        pump(100f, 205f, RED); poly(115f, 205f, 300f, 205f, 300f, 60f, c = RED, w = 5f)
        rect(306f, 70f, 16f, 12f, RED, null, r = 2f); line(300f, 50f, 480f, 50f, RED, 4f); sprinkler(480f, 50f)
        dim(240f, 205f, 240f, 76f); dim(545f, 205f, 545f, 52f)
    },
    "ff-branch" to InputDiagram(listOf(Tag("C", "H-W C", 110f, 40f), Tag("n", "No. of sprinklers", 300f, 40f), Tag("p1", "P at remote spr.", 515f, 40f),
        Tag("lenDef", "Spacing", 300f, 215f), Tag("K", "K-factor", 470f, 215f))) {
        line(60f, 60f, 60f, 200f, RED, 8f); line(60f, 120f, 520f, 120f, RED, 5f)
        listOf(160f, 280f, 400f, 520f).forEach { sprinkler(it, 120f) }
        circle(540f, 95f, 11f, cc.surface, cc.muted, 2.5f)
        dim(280f, 170f, 400f, 170f)
    },
    "hv-cond" to InputDiagram(listOf(Tag("toDb", "Outdoor DB", 110f, 150f), Tag("toWb", "Outdoor WB", 110f, 195f), Tag("tiDb", "Room DB", 430f, 160f),
        Tag("tiRh", "Room RH", 430f, 205f), Tag("tSupply", "Supply air", 430f, 40f), Tag("safety", "Safety", 250f, 232f))) {
        circle(110f, 70f, 26f, Color(0xFFF2B233).copy(alpha = .35f), Color(0xFFE0A21C), 3f)
        for (k in 0 until 8) { val a = Math.toRadians(k * 45.0); line(110f + 34 * kotlin.math.cos(a).toFloat(), 70f + 34 * kotlin.math.sin(a).toFloat(), 110f + 44 * kotlin.math.cos(a).toFloat(), 70f + 44 * kotlin.math.sin(a).toFloat(), Color(0xFFE0A21C), 3f) }
        rect(240f, 20f, 340f, 210f, null, cc.muted, 3f, 4f); line(240f, 60f, 580f, 60f, cc.line, 2f)
        diffuser(330f, 62f, AIR); arrow(330f, 90f, 'd', AIR, 8f); person(520f, 110f)
    },
    "hv-env" to InputDiagram(listOf(Tag("uWall", "Wall U", 70f, 140f), Tag("uRoof", "Roof U", 300f, 22f), Tag("uGlass", "Glass U", 215f, 95f), Tag("sc", "Glass SC", 215f, 140f),
        Tag("clf", "Glass CLF", 215f, 185f), Tag("pplS", "Person sensible", 430f, 105f), Tag("pplL", "Person latent", 430f, 150f), Tag("m2pp", "m² / person", 430f, 195f),
        Tag("rp", "Fresh air / person", 540f, 40f), Tag("ra", "Fresh air / m²", 540f, 238f))) {
        rect(30f, 45f, 520f, 10f, cc.line, cc.muted, 2f, 1f)
        rect(30f, 55f, 22f, 180f, cc.surface2, cc.muted, 2.5f, 1f)
        rect(140f, 80f, 6f, 110f, AIR.copy(alpha = .25f), AIR, 2f, 1f)
        person(500f, 120f)
        line(560f, 60f, 560f, 220f, GREEN, 4f); arrow(560f, 140f, 'd', GREEN, 8f)
    },
    "hv-duct" to InputDiagram(listOf(Tag("depth", "Max depth", 470f, 110f), Tag("rTarget", "Friction rate", 290f, 222f), Tag("vMain", "v main", 95f, 60f),
        Tag("vBranch", "v branch", 95f, 110f), Tag("vRun", "v run-out", 95f, 160f), Tag("step", "Round up to", 470f, 40f))) {
        line(180f, 28f, 420f, 28f, cc.muted, 6f); line(180f, 195f, 420f, 195f, cc.line, 3f)
        rect(220f, 75f, 160f, 80f, AIR.copy(alpha = .18f), AIR, 3f, 2f); arrow(300f, 115f, 'r', AIR, 10f)
        dim(400f, 75f, 400f, 155f)
    },
    "hv-esp" to InputDiagram(listOf(Tag("pTerm", "Diffuser", 430f, 170f), Tag("pRet", "Return path", 300f, 232f), Tag("pDev", "Dampers & devices", 300f, 30f), Tag("safety", "Safety", 540f, 40f))) {
        rect(40f, 70f, 90f, 60f, cc.surface2, cc.muted, 2.5f); fan(100f, 100f, 16f, AIR)
        poly(130f, 90f, 470f, 90f, 470f, 128f, c = AIR, w = 8f); valve(300f, 90f, AIR); diffuser(470f, 132f, AIR)
        poly(470f, 200f, 470f, 205f, 85f, 205f, 85f, 130f, c = PURPLE, w = 6f); rect(452f, 196f, 36f, 6f, PURPLE, null, r = 1f); arrow(280f, 205f, 'l', PURPLE, 8f)
    }
)

/** Draws an input diagram with tappable labels; tapping a label opens that field on the number pad. */
@Composable
fun InputDiagramView(id: String, fields: List<Field>, map: MutableMap<String, String>, app: AppState, onStructural: () -> Unit) {
    val d = DIAGRAMS[id] ?: return
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    val kp = LocalKeypad.current
    val tm = rememberTextMeasurer()
    val tags = d.tags.mapNotNull { t -> fields.firstOrNull { it.k == t.k && it.kind == Kind.NUM }?.let { t to it } }
    @Suppress("UNUSED_VARIABLE") val v = app.version
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, 600.dp)
        val small = w < 440.dp
        val h = w * (if (small) 300f else 250f) / 600f
        Box(Modifier.width(w).height(h).clip(RoundedCornerShape(12.dp)).background(cc.surface2)) {
            Canvas(Modifier.fillMaxSize()) {
                val sc = size.width / 600f
                translate(top = (size.height - 250f * sc) / 2f) { d.draw(Sk(this, sc, tm, cc, accent, -1)) }
            }
            Layout(content = {
                tags.forEach { (t, f) ->
                    val active = kp?.active?.id == fieldId(map, f.k)
                    val value = map[f.k].orEmpty()
                    val valueText = (if (value.isBlank()) "—" else value) + if (f.unit.isNotEmpty()) " " + f.unit else ""
                    val m = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (active) accent else cc.surface)
                        .border(1.5.dp, accent, RoundedCornerShape(8.dp))
                        .clickable { kp?.open(fieldTarget(f, map, app, onStructural)) }
                    if (small) Column(m.padding(horizontal = 5.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.short, fontSize = 8.5.sp, lineHeight = 10.sp, color = if (active) Color.White else cc.muted, maxLines = 1)
                        Text(valueText, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else cc.ink, maxLines = 1)
                    } else Row(m.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.short + "  ", fontSize = 10.5.sp, color = if (active) Color.White else cc.muted, maxLines = 1)
                        Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else cc.ink, maxLines = 1)
                    }
                }
            }) { ms, cons ->
                val W = cons.maxWidth; val H = cons.maxHeight
                val ps = ms.map { it.measure(Constraints()) }
                layout(W, H) {
                    ps.forEachIndexed { i, p ->
                        val t = tags[i].first
                        val x = (t.x / 600f * W - p.width / 2f).toInt().coerceIn(0, maxOf(0, W - p.width))
                        val sc = W / 600f; val top = (H - 250f * sc) / 2f
                        val y = (top + t.y * sc - p.height / 2f).toInt().coerceIn(0, maxOf(0, H - p.height))
                        p.place(x, y)
                    }
                }
            }
        }
    }
}

@Suppress("unused") private fun unusedFmt(x: Double) = fmt(x, 1)
