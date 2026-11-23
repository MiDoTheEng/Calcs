package com.calcs.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A number box that the keypad is editing. */
class NumTarget(val id: String, val label: String, val get: () -> String, val set: (String) -> Unit, val commit: () -> Unit = {})

/**
 * Built-in square number pad (same on every device, no system keyboard).
 * Tapping a number box makes it active; keys edit it; Next jumps to the next box on screen.
 */
class Keypad {
    var active by mutableStateOf<NumTarget?>(null)
        private set
    /** bumped on every key press so the header shows the new value */
    var tick by mutableStateOf(0)
        private set
    private var fresh = true
    private val boxes = HashMap<String, Pair<Offset, NumTarget>>()

    fun register(t: NumTarget, pos: Offset) { boxes[t.id] = pos to t }
    fun unregister(id: String) { boxes.remove(id) }

    fun open(t: NumTarget) {
        if (active?.id != t.id) active?.commit?.invoke()
        active = t; fresh = true; tick++
    }
    fun close() { active?.commit?.invoke(); active = null }

    fun next() {
        val cur = active ?: return
        val me = boxes[cur.id]?.first ?: return close()
        // reading order: top to bottom, then left to right (rows within 8 px count as one row)
        val after = boxes.values.filter { (p, t) -> t.id != cur.id && (p.y > me.y + 8 || (kotlin.math.abs(p.y - me.y) <= 8 && p.x > me.x)) }
            .minWithOrNull(compareBy<Pair<Offset, NumTarget>>({ (it.first.y / 8).toInt() }, { it.first.x }))
        if (after == null) close() else open(after.second)
    }

    fun press(k: String) {
        val t = active ?: return
        var v = t.get()
        when (k) {
            "⌫" -> v = if (fresh) "" else v.dropLast(1)
            "C" -> v = ""
            "±" -> v = if (v.startsWith("-")) v.drop(1) else "-$v"
            "." -> { if (fresh) v = "0." else if (!v.contains('.')) v = (if (v.isEmpty() || v == "-") v + "0" else v) + "." }
            else -> v = if (fresh || v == "0") k else if (v == "-0") "-$k" else v + k
        }
        fresh = false
        t.set(v); tick++
    }
}

val LocalKeypad = staticCompositionLocalOf<Keypad?> { null }

/** Number box driven by the keypad. Falls back to the normal text field where no keypad is provided. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumBox(id: String, label: String, value: String, modifier: Modifier = Modifier, commit: () -> Unit = {}, get: (() -> String)? = null, onChange: (String) -> Unit) {
    val kp = LocalKeypad.current
    if (kp == null) { InputBox(value, true, modifier, onChange); return }
    val cc = LocalCalc.current
    @Suppress("UNUSED_VARIABLE") val tick = kp.tick   // re-read after every key press
    val text = get?.invoke() ?: value   // the stored value is the truth; the pad writes it through onChange
    val target = remember(id) { NumTarget(id, label, get ?: { value }, onChange, commit) }
    val active = kp.active?.id == id
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) { kotlinx.coroutines.delay(180); bring.bringIntoView() } }  // after the pad has appeared
    DisposableEffect(id) { onDispose { kp.unregister(id) } }
    Box(
        modifier
            .height(46.dp)
            .bringIntoViewRequester(bring)
            .onGloballyPositioned { kp.register(target, it.positionInRoot()) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else cc.inBg)
            .border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else cc.inLine, RoundedCornerShape(10.dp))
            .clickable { kp.open(target) }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 16.sp, color = cc.ink, style = T.num, maxLines = 1)
            if (active) Box(Modifier.padding(start = 2.dp).height(20.dp).widthIn(min = 2.dp, max = 2.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

/** The pad itself, shown at the bottom of the screen while a number box is active. */
@Composable
fun KeypadBar(kp: Keypad) {
    val t = kp.active ?: return
    val cc = LocalCalc.current
    val accent = MaterialTheme.colorScheme.primary
    @Suppress("UNUSED_VARIABLE") val tick = kp.tick
    Surface(color = cc.surface, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = cc.line)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label, style = T.small, color = cc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(t.get().ifEmpty { "—" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cc.ink, style = T.num)
                    }
                    val rows = listOf(listOf("7", "8", "9", "⌫"), listOf("4", "5", "6", "C"), listOf("1", "2", "3", "±"), listOf(".", "0", "Next", "Done"))
                    rows.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.forEach { k ->
                                val action = k == "Next" || k == "Done"
                                val fn = k == "⌫" || k == "C" || k == "±"
                                Box(
                                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (k == "Done") accent else if (action || fn) cc.surface2 else cc.inBg)
                                        .border(1.dp, if (k == "Done") accent else cc.line, RoundedCornerShape(12.dp))
                                        .clickable { when (k) { "Next" -> kp.next(); "Done" -> kp.close(); else -> kp.press(k) } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (k == "Next") "Next ›" else k, fontSize = if (action) 16.sp else 22.sp, fontWeight = if (action) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (k == "Done") Color.White else cc.ink, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
