@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package com.calcs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcs.app.core.Branch

/* ============================== model ============================== */

/** A numbered component on a schematic. x, y = badge position in schematic units; tool = tab of the calculation that sizes it. */
class Comp(val n: Int, val name: String, val desc: String, val x: Float, val y: Float, val tool: Int? = null)
class LegendItem(val color: Color, val label: String, val dashed: Boolean = false)
class Schematic(val title: String, val w: Float, val h: Float, val legend: List<LegendItem>, val comps: List<Comp>, val draw: Sk.() -> Unit)

private val CW = Color(0xFF2B7BD8); private val HW = Color(0xFFD8453A)
private val SOIL = Color(0xFF8A5A1E); private val WASTE = Color(0xFF7A8B2E); private val VENT = Color(0xFF8A94A3)
private val STORM = Color(0xFF0E9A90); private val FIRE = Color(0xFFD23C3C)
private val SA = Color(0xFF3E9BE0); private val RA = Color(0xFF8B6BD6); private val FA = Color(0xFF2E9E5B); private val EA = Color(0xFF7D8794)

/* ============================== drawing kit ============================== */

/** Small drawing kit in schematic units (scaled to the canvas). */
class Sk(val ds: DrawScope, val s: Float, val tm: TextMeasurer, val cc: CalcColors, val accent: Color, val sel: Int) {
    /** text / badge scale: follows the drawing but never smaller than ~0.9 dp per unit */
    val ts = maxOf(s, 0.9f * ds.density)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color, w: Float = 4f, dashed: Boolean = false) =
        ds.drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = w * s, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10 * s, 7 * s)) else null)
    fun poly(vararg p: Float, c: Color, w: Float = 4f, dashed: Boolean = false) { var i = 0; while (i + 3 < p.size) { line(p[i], p[i + 1], p[i + 2], p[i + 3], c, w, dashed); i += 2 } }
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Color?, stroke: Color? = null, sw: Float = 2f, r: Float = 4f) {
        if (fill != null) ds.drawRoundRect(fill, o(x, y), Size(w * s, h * s), CornerRadius(r * s))
        if (stroke != null) ds.drawRoundRect(stroke, o(x, y), Size(w * s, h * s), CornerRadius(r * s), style = Stroke(sw * s))
    }
    fun circle(x: Float, y: Float, r: Float, fill: Color?, stroke: Color? = null, sw: Float = 2f) {
        if (fill != null) ds.drawCircle(fill, r * s, o(x, y))
        if (stroke != null) ds.drawCircle(stroke, r * s, o(x, y), style = Stroke(sw * s))
    }
    fun text(x: Float, y: Float, t: String, fs: Float = 12f, c: Color = cc.ink, bold: Boolean = false, center: Boolean = true) {
        val st = TextStyle(fontSize = with(ds) { (fs * ts).toSp() }, color = c, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        val l = tm.measure(t, st)
        ds.drawText(l, topLeft = if (center) Offset(x * s - l.size.width / 2f, y * s - l.size.height / 2f) else Offset(x * s, y * s - l.size.height / 2f))
    }
    fun arrow(x: Float, y: Float, dir: Char, c: Color, sz: Float = 9f) {
        val p = Path()
        when (dir) {
            'r' -> { p.moveTo((x + sz) * s, y * s); p.lineTo((x - sz) * s, (y - sz * .7f) * s); p.lineTo((x - sz) * s, (y + sz * .7f) * s) }
            'l' -> { p.moveTo((x - sz) * s, y * s); p.lineTo((x + sz) * s, (y - sz * .7f) * s); p.lineTo((x + sz) * s, (y + sz * .7f) * s) }
            'u' -> { p.moveTo(x * s, (y - sz) * s); p.lineTo((x - sz * .7f) * s, (y + sz) * s); p.lineTo((x + sz * .7f) * s, (y + sz) * s) }
            else -> { p.moveTo(x * s, (y + sz) * s); p.lineTo((x - sz * .7f) * s, (y - sz) * s); p.lineTo((x + sz * .7f) * s, (y - sz) * s) }
        }
        p.close(); ds.drawPath(p, c)
    }
    fun pump(x: Float, y: Float, c: Color, r: Float = 15f) {
        circle(x, y, r, cc.surface, c, 3f)
        val p = Path(); p.moveTo((x - r * .45f) * s, (y - r * .55f) * s); p.lineTo((x + r * .6f) * s, y * s); p.lineTo((x - r * .45f) * s, (y + r * .55f) * s); p.close()
        ds.drawPath(p, c)
    }
    fun valve(x: Float, y: Float, c: Color, vertical: Boolean = false, sz: Float = 9f) {
        val p = Path()
        if (!vertical) { p.moveTo((x - sz) * s, (y - sz * .7f) * s); p.lineTo((x + sz) * s, (y + sz * .7f) * s); p.lineTo((x + sz) * s, (y - sz * .7f) * s); p.lineTo((x - sz) * s, (y + sz * .7f) * s) }
        else { p.moveTo((x - sz * .7f) * s, (y - sz) * s); p.lineTo((x + sz * .7f) * s, (y + sz) * s); p.lineTo((x - sz * .7f) * s, (y + sz) * s); p.lineTo((x + sz * .7f) * s, (y - sz) * s) }
        p.close(); ds.drawPath(p, cc.surface); ds.drawPath(p, c, style = Stroke(2.5f * s))
    }
    fun tank(x: Float, y: Float, w: Float, h: Float, c: Color, label: String) {
        rect(x, y, w, h, cc.surface, cc.muted, 2.5f)
        rect(x + 3, y + h * .3f, w - 6, h * .7f - 3, c.copy(alpha = .22f), null, r = 2f)
        line(x + 3, y + h * .3f, x + w - 3, y + h * .3f, c, 2f)
        text(x + w / 2, y + h * .62f, label, 11f, cc.ink, true)
    }
    fun building(x: Float, w: Float, roof: Float, ground: Float, floors: List<Float>, names: List<String>) {
        line(x, roof, x + w, roof, cc.muted, 5f)
        floors.forEach { line(x, it, x + w, it, cc.line, 4f) }
        line(x, ground, x + w, ground, cc.muted, 5f)
        line(x, roof, x, ground, cc.line, 3f); line(x + w, roof, x + w, ground, cc.line, 3f)
        val levels = listOf(roof) + floors + listOf(ground)
        names.forEachIndexed { i, n -> if (i + 1 < levels.size) text(x + w - 8, (levels[i] + levels[i + 1]) / 2, n, 10f, cc.muted, center = false) }
    }
    fun groundLine(x1: Float, x2: Float, y: Float) {
        line(x1, y, x2, y, cc.muted, 2.5f)
        var x = x1; while (x < x2) { line(x, y + 2, x - 8, y + 12, cc.line, 1.5f); x += 16 }
    }
    fun fixture(x: Float, y: Float, kind: String) {
        when (kind) {
            "wc" -> { rect(x - 9, y - 16, 18f, 10f, cc.surface, cc.muted, 2f); rect(x - 11, y - 6, 22f, 12f, cc.surface, cc.muted, 2f, 6f) }
            "lav" -> { rect(x - 12, y - 8, 24f, 10f, cc.surface, cc.muted, 2f, 5f); line(x, y + 2, x, y + 14, cc.muted, 2f) }
            "shower" -> { line(x, y - 20, x, y + 6, cc.muted, 2f); line(x, y - 20, x + 10, y - 20, cc.muted, 2f); rect(x + 6, y - 22, 10f, 5f, cc.muted, null) }
            else -> rect(x - 13, y - 7, 26f, 12f, cc.surface, cc.muted, 2f, 2f)
        }
    }
    fun sprinkler(x: Float, y: Float) { line(x, y, x, y + 10, FIRE, 2.5f); line(x - 6, y + 12, x + 6, y + 12, FIRE, 2.5f) }
    fun diffuser(x: Float, y: Float, c: Color) { val p = Path(); p.moveTo((x - 16) * s, y * s); p.lineTo((x + 16) * s, y * s); p.lineTo((x + 10) * s, (y + 7) * s); p.lineTo((x - 10) * s, (y + 7) * s); p.close(); ds.drawPath(p, c) }
    fun fan(x: Float, y: Float, r: Float, c: Color) {
        circle(x, y, r, cc.surface, c, 2.5f)
        for (k in 0 until 3) { val a = Math.toRadians(k * 120.0); line(x, y, x + (r * .8f * kotlin.math.cos(a)).toFloat(), y + (r * .8f * kotlin.math.sin(a)).toFloat(), c, 2.5f) }
    }
    fun badge(c: Comp) {
        val on = c.n == sel
        val k = ts / s
        circle(c.x, c.y, (if (on) 15f else 12.5f) * k, if (on) accent else cc.surface, accent, 2.5f)
        text(c.x, c.y, c.n.toString(), 11.5f, if (on) Color.White else accent, true)
    }
}

/* ============================== the five systems ============================== */

fun schematicFor(id: String): Schematic? = when (id) {
    "water" -> waterSchem
    "drainage" -> drainSchem
    "rain" -> rainSchem
    "fire" -> fireSchem
    "hvac" -> hvacSchem
    else -> null
}

private val waterSchem = Schematic("Water supply – tank & booster system", 1000f, 640f,
    listOf(LegendItem(CW, "Cold water"), LegendItem(HW, "Hot water")),
    listOf(
        Comp(1, "Municipal main & water meter", "Authority supply into the plot. The meter and isolating valve sit at the boundary; the main fills the ground tank through a float valve.", 70f, 568f),
        Comp(2, "Ground (lower) tank", "Stores the daily demand × storage days (often split between ground and roof tanks). Sized from persons × L/p/day.", 210f, 470f, 0),
        Comp(3, "Transfer pumps (duty / standby)", "Lift water from the ground tank to the roof tank. Flow ≈ daily demand refilled in a few hours; head = static height + friction.", 292f, 600f, 0),
        Comp(4, "Roof tank", "Upper storage that feeds the building. Its outlet goes to the booster set or straight down by gravity.", 392f, 40f, 0),
        Comp(5, "Booster pump set", "Gives the top floors enough residual pressure. Duty point = peak flow (WSFU → Hunter) at static + friction + residual head.", 492f, 60f, 0),
        Comp(6, "Down-feed riser", "Main vertical pipe serving each floor. Sized for the peak flow of all floors above each section.", 552f, 330f, 1),
        Comp(7, "Pressure reducing valve (PRV)", "On the lower floors static pressure builds up; a PRV keeps fixtures below the allowed maximum.", 588f, 400f),
        Comp(8, "Apartment branch & fixtures", "Each apartment branch is sized from its own WSFU. Friction is added segment by segment along the critical path.", 742f, 300f, 1),
        Comp(9, "Water heater", "Storage volume and heater power from the fixtures it serves (fixture method).", 690f, 150f, 2)
    )) {
    groundLine(0f, 1000f, 560f)
    building(330f, 470f, 120f, 560f, listOf(230f, 340f, 450f), listOf("Floor 3", "Floor 2", "Floor 1", "Ground"))
    // main → meter → ground tank
    poly(20f, 600f, 140f, 600f, 140f, 545f, 160f, 545f, c = CW); valve(105f, 600f, CW); text(60f, 620f, "Municipal main", 10f, cc.muted)
    tank(160f, 495f, 110f, 80f, CW, "Ground tank")
    // transfer pumps → riser up → roof tank
    poly(270f, 565f, 292f, 565f, c = CW); pump(292f, 565f, CW); pump(292f, 530f, CW, 11f)
    poly(307f, 565f, 345f, 565f, 345f, 95f, 360f, 95f, c = CW); arrow(345f, 300f, 'u', CW)
    tank(360f, 60f, 100f, 55f, CW, "Roof tank")
    // booster → down-feed riser
    poly(460f, 105f, 480f, 105f, c = CW); pump(492f, 105f, CW)
    poly(507f, 105f, 530f, 105f, 530f, 430f, c = CW); arrow(530f, 250f, 'd', CW)
    // floor branches
    listOf(200f, 310f, 420f).forEachIndexed { i, y ->
        poly(530f, y, 780f, y, c = CW); arrow(640f, y, 'r', CW, 7f)
        line(640f, y, 640f, y + 18, CW, 3f); fixture(640f, y + 30, "wc")
        line(700f, y, 700f, y + 18, CW, 3f); fixture(700f, y + 30, "lav")
        line(760f, y, 760f, y + 8, CW, 3f); fixture(760f, y + 28, "shower")
        if (i == 2) valve(570f, y, CW)
    }
    // heater on the top floor, hot water in red
    rect(665f, 160f, 30f, 34f, cc.surface, HW, 2.5f); text(680f, 177f, "WH", 9f, HW, true)
    poly(680f, 194f, 680f, 212f, 705f, 212f, 705f, 222f, c = HW, w = 3f); poly(680f, 212f, 760f, 212f, 760f, 220f, c = HW, w = 3f)
}

private val drainSchem = Schematic("Drainage – two-pipe system (soil & waste)", 1000f, 660f,
    listOf(LegendItem(SOIL, "Soil (WC, urinal)"), LegendItem(WASTE, "Waste (basin, shower, sink)"), LegendItem(VENT, "Vent", true)),
    listOf(
        Comp(1, "WC & soil branch", "Horizontal soil branch from the WC to the soil stack. Never below 3\" when it serves a WC; sized from the DFU it carries.", 290f, 205f, 1),
        Comp(2, "Basin / shower & waste branch", "Waste fixtures drain to the waste stack through their own branch, sized from its DFU.", 690f, 205f, 1),
        Comp(3, "Soil stack", "Collects the soil branches of all floors. Above 3 branch intervals check both the total DFU and the DFU at one interval.", 448f, 330f, 1),
        Comp(4, "Waste stack", "Separate stack for waste fixtures (two-pipe system). Joins the soil drain at ground level through a gully / IC.", 540f, 330f, 1),
        Comp(5, "Vent stack & branch vents", "Lets air in and out so traps are not siphoned. Vent ≥ ½ the drain it serves, min 1¼\".", 790f, 330f, 1),
        Comp(6, "Vent terminals at roof", "Stacks continue through the roof open to air, away from windows and fresh-air intakes.", 520f, 52f),
        Comp(7, "Floor / gully trap", "Waste water passes a trapped gully before it meets the soil drain, so smells can't come back up.", 615f, 600f),
        Comp(8, "Building drain (sloped)", "Carries the whole building to the manhole. Size from the total DFU and the slope (1, 2 or 4 %).", 720f, 640f, 1),
        Comp(9, "Manhole / inspection chamber", "Access for cleaning at every change of direction and at the plot boundary.", 860f, 560f),
        Comp(10, "Municipal sewer", "Connection to the authority sewer (or septic / treatment where none exists).", 960f, 610f),
        Comp(11, "Fixture DFU", "Every fixture has a drainage fixture unit value; add them up per branch, stack and building.", 360f, 268f, 0)
    )) {
    groundLine(0f, 1000f, 580f)
    building(240f, 600f, 120f, 580f, listOf(230f, 340f, 450f), listOf("Floor 3", "Floor 2", "Floor 1", "Ground"))
    val soilX = 430f; val wasteX = 520f; val ventX = 770f
    // stacks + roof vents
    line(soilX, 70f, soilX, 610f, SOIL, 6f); line(wasteX, 70f, wasteX, 585f, WASTE, 6f)
    line(soilX - 9, 70f, soilX + 9, 70f, SOIL, 4f); line(wasteX - 9, 70f, wasteX + 9, 70f, WASTE, 4f)
    poly(ventX, 160f, ventX, 70f, c = VENT, w = 3.5f, dashed = true); line(ventX - 8, 70f, ventX + 8, 70f, VENT, 3f)
    poly(ventX, 160f, ventX, 545f, c = VENT, w = 3.5f, dashed = true)
    listOf(230f, 340f, 450f, 560f).forEach { fl ->
        val y = fl - 28f
        // WC → soil stack
        fixture(330f, fl - 12f, "wc"); poly(330f, fl - 6f, 330f, y + 14, soilX, y + 22, c = SOIL, w = 4f)
        // basin + shower → waste stack, branch vent up to vent stack
        fixture(640f, fl - 26f, "lav"); fixture(700f, fl - 22f, "shower")
        poly(640f, fl - 12f, 640f, y + 14, wasteX, y + 22, c = WASTE, w = 4f); poly(700f, fl - 10f, 700f, y + 14, 640f, y + 14, c = WASTE, w = 3f)
        poly(650f, y + 14, 650f, y - 20, ventX, y - 20, c = VENT, w = 2.5f, dashed = true)
        text(330f, fl - 50f, "WC", 9f, cc.muted); text(670f, fl - 58f, "Basin · Shower", 9f, cc.muted)
    }
    // gully + building drain + manhole + sewer
    poly(wasteX, 585f, 600f, 585f, 600f, 600f, c = WASTE, w = 5f); rect(590f, 598f, 22f, 16f, cc.surface, WASTE, 2.5f)
    poly(soilX, 610f, 612f, 620f, 850f, 628f, c = SOIL, w = 6f); line(600f, 614f, 612f, 620f, WASTE, 4f)
    arrow(740f, 624f, 'r', SOIL)
    rect(845f, 585f, 34f, 56f, cc.surface2, cc.muted, 2.5f); text(862f, 612f, "MH", 9f, cc.muted, true)
    poly(879f, 632f, 990f, 640f, c = SOIL, w = 6f); text(945f, 655f, "To sewer", 10f, cc.muted)
}

private val rainSchem = Schematic("Rainwater – roof drains to storm network", 1000f, 640f,
    listOf(LegendItem(STORM, "Storm water")),
    listOf(
        Comp(1, "Roof drain", "Collects rain from its share of the roof. Q = C · i · A / 3600 for that area.", 300f, 112f, 0),
        Comp(2, "Overflow scupper / drain", "Second path if a drain blocks, through the parapet at a higher level. Never connected to the primary drains.", 800f, 112f),
        Comp(3, "Rainwater leader (RWP)", "Vertical pipe sized from the flow per leader (IPC 1106.2). Use at least two per roof.", 312f, 360f, 0),
        Comp(4, "Horizontal storm drain", "Collects the leaders at ground level, sized by Manning for its flow and slope.", 600f, 618f, 1),
        Comp(5, "Area drain (paving / landscape)", "Paved and landscaped areas drain into the same network with a lower runoff coefficient C.", 110f, 540f, 0),
        Comp(6, "Storm manhole", "Access at junctions and changes of direction.", 868f, 545f),
        Comp(7, "Storm network / soakaway", "Authority storm sewer, or soakaway / tank where the authority requires on-site disposal.", 960f, 610f)
    )) {
    // rain
    var x = 230f; while (x < 760f) { line(x, 28f, x - 8, 48f, STORM.copy(alpha = .55f), 2f); x += 38f }
    groundLine(0f, 1000f, 560f)
    // roof with parapets
    line(220f, 150f, 780f, 150f, cc.muted, 6f); line(220f, 115f, 220f, 150f, cc.muted, 6f); line(780f, 115f, 780f, 150f, cc.muted, 6f)
    line(220f, 150f, 220f, 560f, cc.line, 3f); line(780f, 150f, 780f, 560f, cc.line, 3f)
    listOf(260f, 370f, 480f).forEach { line(220f, it, 780f, it, cc.line, 3f) }
    // roof drains + leaders
    listOf(300f, 680f).forEach { dx ->
        val p = Path(); p.moveTo((dx - 14) * s, 142f * s); p.lineTo((dx + 14) * s, 142f * s); p.lineTo((dx + 6) * s, 152f * s); p.lineTo((dx - 6) * s, 152f * s); p.close()
        ds.drawPath(p, STORM)
        line(dx, 152f, dx, 590f, STORM, 5f); arrow(dx, 300f, 'd', STORM)
    }
    // scupper through the parapet
    rect(778f, 126f, 30f, 8f, STORM, null, r = 1f); poly(808f, 130f, 830f, 150f, c = STORM, w = 2f, dashed = true)
    // area drain on paving
    rect(98f, 552f, 24f, 8f, STORM, null, r = 1f); poly(110f, 560f, 110f, 600f, 300f, 600f, c = STORM, w = 4f)
    text(110f, 515f, "Paving", 10f, cc.muted)
    // storm drain → manhole → network
    poly(300f, 590f, 300f, 604f, 850f, 616f, c = STORM, w = 6f); line(680f, 590f, 680f, 612f, STORM, 5f); arrow(520f, 608f, 'r', STORM)
    rect(850f, 575f, 36f, 56f, cc.surface2, cc.muted, 2.5f); text(868f, 603f, "SMH", 9f, cc.muted, true)
    poly(886f, 622f, 990f, 628f, c = STORM, w = 6f)
}

private val fireSchem = Schematic("Fire fighting – pumps, wet riser & sprinklers", 1000f, 640f,
    listOf(LegendItem(FIRE, "Fire water (wet)"), LegendItem(CW, "Tank make-up")),
    listOf(
        Comp(1, "Fire water tank", "UAE: at least 60 min of the fire pump's rated flow (or the zone demand if larger).", 100f, 455f, 0),
        Comp(2, "Electric fire pump (duty)", "Rated flow = largest of sprinkler + hose demand and the riser demand; head = largest needed.", 205f, 610f, 1),
        Comp(3, "Diesel fire pump (standby)", "Same duty as the electric pump, runs if mains power fails.", 260f, 610f, 1),
        Comp(4, "Jockey pump", "Small pump that keeps the system pressurised and covers small leaks so the main pumps don't start.", 312f, 610f),
        Comp(5, "Alarm check valve", "Wet-system control valve with alarm, gauges and test & drain.", 395f, 485f),
        Comp(6, "Wet riser (Class III)", "Vertical main, ≥ 150 mm, feeding landing valves and floor sprinkler zones.", 458f, 380f, 0),
        Comp(7, "Landing valve (65 mm)", "Fire brigade hose outlet at every floor: 250 gpm at ≈ 6.9 bar at the remotest valve.", 470f, 160f, 0),
        Comp(8, "Hose reel", "First-aid hose for occupants: 25 mm × 30 m, ≈ 6.5 gpm at 4.5 bar.", 520f, 175f),
        Comp(9, "Zone control valve", "Per-floor valve with flow switch and tamper switch, so each floor alarms on its own.", 560f, 245f),
        Comp(10, "Sprinkler branch lines", "Hydraulic calc runs sprinkler by sprinkler from the most remote one; check pressure and velocity.", 760f, 245f, 2),
        Comp(11, "Fire brigade inlet", "Breeching inlet on the façade so the fire brigade can pump into the riser.", 352f, 520f)
    )) {
    groundLine(0f, 1000f, 560f)
    building(410f, 560f, 120f, 560f, listOf(230f, 340f, 450f), listOf("Floor 3", "Floor 2", "Floor 1", "Ground"))
    // pump room
    rect(20f, 470f, 360f, 160f, null, cc.line, 2f); text(40f, 482f, "Pump room", 10f, cc.muted, center = false)
    tank(40f, 500f, 120f, 110f, FIRE, "Fire tank"); poly(20f, 520f, 40f, 520f, c = CW, w = 3f)
    poly(160f, 595f, 205f, 595f, c = FIRE); pump(205f, 595f, FIRE); poly(160f, 595f, 160f, 620f, 260f, 620f, 260f, 610f, c = FIRE, w = 3f)
    pump(260f, 595f, FIRE); text(260f, 572f, "D", 10f, FIRE, true); pump(312f, 595f, FIRE, 10f); poly(160f, 620f, 312f, 620f, 312f, 605f, c = FIRE, w = 2.5f)
    poly(205f, 580f, 205f, 540f, 395f, 540f, c = FIRE); line(260f, 580f, 260f, 540f, FIRE, 4f); line(312f, 585f, 312f, 540f, FIRE, 3f)
    valve(395f, 520f, FIRE, true); rect(385f, 498f, 20f, 10f, FIRE, null)
    poly(395f, 540f, 395f, 510f, c = FIRE); poly(395f, 498f, 395f, 470f, 440f, 470f, c = FIRE)
    // riser
    line(440f, 470f, 440f, 130f, FIRE, 7f); arrow(440f, 300f, 'u', FIRE)
    // fire brigade inlet on the façade
    poly(352f, 540f, 352f, 500f, 440f, 500f, c = FIRE, w = 3.5f); circle(352f, 545f, 7f, cc.surface, FIRE, 2.5f)
    listOf(230f, 340f, 450f).forEach { fl ->
        val y = fl - 70f
        rect(452f, fl - 32f, 18f, 14f, FIRE, null, r = 2f)                   // landing valve
        circle(502f, fl - 25f, 9f, cc.surface, FIRE, 2.5f); circle(502f, fl - 25f, 3f, FIRE)   // hose reel
        line(440f, y, 940f, y, FIRE, 4f); valve(560f, y, FIRE)                 // zone valve + cross main
        var sx = 610f; while (sx <= 920f) { sprinkler(sx, y); sx += 52f }
    }
}

private val REF = Color(0xFFE07B1A); private val COND = Color(0xFF6D8FA8)

private val hvacSchem = Schematic("HVAC – VRF & split units, fresh air, exhaust", 1000f, 600f,
    listOf(LegendItem(REF, "Refrigerant (liquid + gas)"), LegendItem(FA, "Fresh air"), LegendItem(SA, "Supply air"), LegendItem(RA, "Return air"),
        LegendItem(EA, "Exhaust air"), LegendItem(COND, "Condensate drain", true)),
    listOf(
        Comp(1, "VRF outdoor unit", "One outdoor unit serves many indoor units. Size it from the sum of the indoor loads with diversity; keep the combination ratio within the maker's limits (≈ 50–130 %) and check pipe length / height limits.", 150f, 40f, 0),
        Comp(2, "Refrigerant piping & branch joints", "Liquid + gas pipes with Y-joints (refnets) to each indoor unit. Sizes come from the manufacturer's selection software, based on the downstream indoor capacity.", 330f, 128f),
        Comp(3, "Ducted indoor unit (concealed)", "Serves a large room through ducts. Select it from the room's total load (TR / kW) and its supply airflow; its ESP comes from the duct calculation.", 238f, 228f, 0),
        Comp(4, "Supply duct & diffusers", "Sized by equal friction; the duct losses + diffuser give the ESP the ducted unit must deliver.", 335f, 268f, 2),
        Comp(5, "Return grille / ceiling plenum", "Room air back to the indoor unit, ducted or through the ceiling void.", 120f, 275f, 2),
        Comp(6, "Cassette indoor unit", "Ceiling unit for rooms without ducts (meetings, shops). Pick the size from the room load.", 570f, 278f, 0),
        Comp(7, "Wall-mounted split + its outdoor unit", "One indoor unit with its own condensing unit on the roof – for single rooms, offices, apartments.", 785f, 352f, 0),
        Comp(8, "Fresh air unit (FAHU / DX)", "Treats the outdoor air needed by ASHRAE 62.1 so the indoor units only handle the room load.", 390f, 42f, 1),
        Comp(9, "Fresh air duct & grilles", "Delivers the treated fresh air to each space at its ventilation rate.", 650f, 205f, 1),
        Comp(10, "Toilet exhaust & exhaust fan", "Keeps toilets negative; rate per WC or per m² (ASHRAE 62.1 Table 6-4).", 912f, 52f, 1),
        Comp(11, "Condensate drain", "From every indoor unit to a trapped drain point, sloped ≥ 1 %. Cassettes / ducted units often need a small lift pump.", 712f, 440f),
        Comp(12, "Rooms, loads & controller", "Each room's cooling load (walls, glass, people, lights, equipment) sets the unit size; a wired remote controller / thermostat per room.", 300f, 450f, 0)
    )) {
    // roof, ceiling void, rooms
    line(40f, 130f, 970f, 130f, cc.muted, 6f); line(40f, 250f, 970f, 250f, cc.line, 3f); line(40f, 560f, 970f, 560f, cc.muted, 6f)
    line(40f, 130f, 40f, 560f, cc.line, 3f); line(970f, 130f, 970f, 560f, cc.line, 3f)
    listOf(440f, 700f, 860f).forEach { line(it, 250f, it, 560f, cc.line, 3f) }
    text(50f, 240f, "Ceiling void", 10f, cc.muted, center = false)
    text(240f, 525f, "Office", 13f, cc.muted, true); text(570f, 525f, "Meeting", 13f, cc.muted, true)
    text(780f, 525f, "Manager", 13f, cc.muted, true); text(915f, 525f, "Toilet", 12f, cc.muted, true)
    // VRF outdoor unit on the roof
    rect(80f, 62f, 140f, 62f, cc.surface2, cc.muted, 2.5f); fan(118f, 85f, 14f, cc.muted); fan(182f, 85f, 14f, cc.muted); text(150f, 115f, "VRF outdoor", 10f, cc.ink, true)
    // refrigerant main with Y-joints
    poly(150f, 124f, 150f, 145f, 570f, 145f, c = REF, w = 4f); poly(160f, 124f, 160f, 152f, 560f, 152f, c = REF, w = 2.5f)
    poly(240f, 145f, 240f, 165f, c = REF, w = 4f); circle(240f, 145f, 4f, REF)
    poly(570f, 145f, 570f, 230f, c = REF, w = 4f); circle(570f, 145f, 4f, REF)
    // ducted indoor unit + supply duct + diffusers + return
    rect(200f, 165f, 80f, 45f, cc.surface2, cc.muted, 2.5f); fan(258f, 187f, 11f, SA); text(222f, 187f, "IU", 10f, cc.ink, true)
    poly(280f, 180f, 410f, 180f, c = SA, w = 8f); arrow(345f, 180f, 'r', SA, 7f)
    listOf(310f, 390f).forEach { dx -> line(dx, 180f, dx, 246f, SA, 5f); diffuser(dx, 250f, SA) }
    rect(102f, 250f, 36f, 7f, RA, null, r = 1f); poly(120f, 250f, 120f, 190f, 200f, 190f, c = RA, w = 6f); arrow(165f, 190f, 'r', RA, 7f)
    // cassette in the meeting room
    rect(540f, 230f, 60f, 22f, cc.surface2, cc.muted, 2.5f); text(570f, 241f, "Cassette", 8.5f, cc.ink, true)
    arrow(530f, 262f, 'l', SA, 6f); arrow(610f, 262f, 'r', SA, 6f)
    // wall split + its own outdoor unit
    rect(745f, 290f, 90f, 30f, cc.surface2, cc.muted, 2.5f); text(790f, 305f, "Wall split", 9f, cc.ink, true)
    rect(760f, 82f, 70f, 42f, cc.surface2, cc.muted, 2.5f); fan(780f, 103f, 11f, cc.muted); text(812f, 103f, "ODU", 8.5f, cc.ink, true)
    poly(825f, 290f, 825f, 124f, c = REF, w = 4f)
    // fresh air unit on the roof → fresh air duct
    rect(330f, 62f, 120f, 56f, cc.surface2, FA, 2.5f); text(390f, 90f, "FAHU", 11f, cc.ink, true)
    poly(490f, 90f, 450f, 90f, c = FA, w = 5f); arrow(468f, 90f, 'l', FA); text(500f, 90f, "Outdoor air", 9f, FA, center = false)
    poly(410f, 118f, 410f, 205f, 650f, 205f, 650f, 248f, c = FA, w = 5f); poly(410f, 205f, 290f, 205f, 290f, 210f, c = FA, w = 4f)
    rect(632f, 250f, 36f, 6f, FA, null, r = 1f)
    // toilet exhaust
    rect(895f, 250f, 34f, 7f, EA, null, r = 1f); poly(912f, 250f, 912f, 110f, c = EA, w = 6f); fan(912f, 98f, 15f, EA); arrow(912f, 66f, 'u', EA)
    // condensate drains
    poly(600f, 240f, 690f, 240f, 690f, 556f, c = COND, w = 2.5f, dashed = true)
    poly(835f, 318f, 850f, 318f, 850f, 556f, c = COND, w = 2.5f, dashed = true)
    poly(200f, 205f, 190f, 205f, c = COND, w = 2.5f, dashed = true)
    // controller + people
    rect(420f, 350f, 14f, 22f, cc.surface, cc.muted, 2f)
    listOf(180f, 250f, 320f).forEach { px -> circle(px, 420f, 9f, null, cc.muted, 2.5f); line(px, 429f, px, 465f, cc.muted, 3f); line(px - 12, 442f, px + 12, 442f, cc.muted, 3f) }
}

/* ============================== screen ============================== */

@Composable
fun OverviewContent(b: Branch, avail: Dp, openTool: (Int) -> Unit) {
    val sch = schematicFor(b.id) ?: return
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    var sel by remember(b.id) { mutableIntStateOf(1) }
    val tm = rememberTextMeasurer()
    val inner = avail - 32.dp
    val wide = avail >= 1000.dp
    val mapW = if (wide) (inner - 14.dp) * 0.64f else inner      // panel width
    val canvasW = maxOf(mapW - 32.dp, 640.dp)                     // phones scroll sideways
    val canvasH = canvasW * (sch.h / sch.w)
    val listW = if (wide) inner - mapW - 14.dp else inner
    val drawing: @Composable () -> Unit = {
        Panel(sch.title) {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier.width(canvasW).height(canvasH).pointerInput(b.id, canvasW) {
                        detectTapGestures { p ->
                            val s = size.width / sch.w
                            val hit = sch.comps.minByOrNull { c -> val dx = c.x * s - p.x; val dy = c.y * s - p.y; dx * dx + dy * dy }
                            if (hit != null) {
                                val dx = hit.x * s - p.x; val dy = hit.y * s - p.y
                                val r = maxOf(40 * s, 28.dp.toPx())
                                if (dx * dx + dy * dy < r * r) sel = hit.n
                            }
                        }
                    }
                ) {
                    val k = Sk(this, size.width / sch.w, tm, cc, accent, sel)
                    sch.draw(k)
                    sch.comps.forEach { k.badge(it) }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sch.legend.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.width(26.dp).height(8.dp)) {
                            drawLine(l.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 4.dp.toPx(),
                                pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
                        }
                        Spacer(Modifier.width(6.dp)); Text(l.label, style = T.small, color = cc.ink)
                    }
                }
            }
        }
    }
    val detail: @Composable () -> Unit = {
        val c = sch.comps.firstOrNull { it.n == sel }
        if (c != null) Surface(shape = RoundedCornerShape(16.dp), color = cc.surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) { Text(c.n.toString(), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = cc.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(c.desc, color = cc.ink)
                    c.tool?.let { t -> Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { openTool(t) }) { Text("Open “${b.tools[t].title}” →") } }
                }
            }
        }
    }
    val list: @Composable () -> Unit = {
        Panel("Components") {
            val two = !wide && avail >= 720.dp
            val rowW = if (two) (listW - 32.dp - 10.dp) / 2 else listW - 32.dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sch.comps.forEach { c ->
                    val on = c.n == sel
                    Row(
                        Modifier.width(rowW).clip(RoundedCornerShape(12.dp))
                            .background(if (on) accent.copy(alpha = .12f) else cc.surface2)
                            .border(1.dp, if (on) accent else cc.line, RoundedCornerShape(12.dp))
                            .clickable { sel = c.n }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (on) accent else cc.surface).border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text(c.n.toString(), color = if (on) Color.White else accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold, color = cc.ink)
                            c.tool?.let { SmallLabel("Sized in: ${b.tools[it].title}") }
                        }
                    }
                }
            }
        }
    }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "title") {
            Column(Modifier.widthIn(max = 1240.dp).fillMaxWidth()) {
                Text("${b.title} – system overview", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cc.ink)
                Text("Tap a number on the drawing or in the list. Each component links to the calculation that sizes it.", color = cc.muted)
            }
        }
        if (wide) {
            item(key = "wide") {
                Row(Modifier.widthIn(max = 1240.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(mapW)) { drawing() }
                    Column(Modifier.width(listW), verticalArrangement = Arrangement.spacedBy(14.dp)) { detail(); list() }
                }
            }
        } else {
            item(key = "map") { Box(Modifier.widthIn(max = 1240.dp)) { drawing() } }
            item(key = "selected") { Box(Modifier.widthIn(max = 1240.dp)) { detail() } }
            item(key = "list") { Box(Modifier.widthIn(max = 1240.dp)) { list() } }
        }
    }
}
