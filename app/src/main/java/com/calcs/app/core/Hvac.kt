package com.calcs.app.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/* HVAC: cooling load (room by room), fresh air & exhaust (ASHRAE 62.1), duct sizing & ESP. */
object Hvac {
    const val TR_KW = 3.51685
    const val LS_CFM = 2.11888
    const val P_ATM = 101.325

    /* --------------------------- psychrometrics --------------------------- */
    fun pws(t: Double) = 0.61094 * exp(17.625 * t / (t + 243.04))                // kPa (Magnus)
    fun wSat(t: Double) = 0.621945 * pws(t) / (P_ATM - pws(t))
    fun wFromWb(db: Double, wb: Double): Double {
        val ws = wSat(wb)
        return ((2501 - 2.326 * wb) * ws - 1.006 * (db - wb)) / (2501 + 1.86 * db - 4.186 * wb)
    }
    fun wFromRh(db: Double, rh: Double): Double { val pw = rh / 100 * pws(db); return 0.621945 * pw / (P_ATM - pw) }
    fun enthalpy(db: Double, w: Double) = 1.006 * db + w * (2501 + 1.86 * db)

    /* ------------------------------ orientation ------------------------------ */
    // Peak values for ~24° N in summer (UAE). Editable in the tool.
    val ORIENT = listOf(  // name, wall ETD (K), glass solar heat gain factor (W/m²)
        Triple("N", 14.0, 130.0), Triple("NE", 18.0, 460.0), Triple("E", 22.0, 640.0), Triple("SE", 20.0, 470.0),
        Triple("S", 18.0, 220.0), Triple("SW", 24.0, 470.0), Triple("W", 26.0, 640.0), Triple("NW", 20.0, 460.0),
        Triple("Horizontal", 30.0, 800.0)
    )

    fun coolingLoad(s: ToolState): Res {
        val r = Res()
        val to = s.n0("toDb"); val ti = s.n0("tiDb")
        val wo = wFromWb(to, s.n0("toWb")); val wi = wFromRh(ti, s.n0("tiRh"))
        val dT = to - ti; val dW = max(0.0, wo - wi)
        r["wo"] = wo * 1000; r["wi"] = wi * 1000; r["dT"] = dT; r["dW"] = dW * 1000
        r["ho"] = enthalpy(to, wo); r["hi"] = enthalpy(ti, wi)
        val sf = 1 + s.n0("safety") / 100
        val dTs = ti - s.n0("tSupply")
        val orient = s.list("orient")
        fun etd(o: String) = ORIENT.indexOfFirst { it.first == o }.let { if (it < 0) 0.0 else orient.getOrNull(it)?.n0("etd") ?: ORIENT[it].second }
        fun shgf(o: String) = ORIENT.indexOfFirst { it.first == o }.let { if (it < 0) 0.0 else orient.getOrNull(it)?.n0("shgf") ?: ORIENT[it].third }
        val roofEtd = orient.getOrNull(8)?.n0("etd") ?: ORIENT[8].second

        var tA = 0.0; var tRsh = 0.0; var tRlh = 0.0; var tOa = 0.0; var tOaL = 0.0; var tTot = 0.0; var tSa = 0.0; var tPpl = 0.0
        s.list("rooms").forEach { g ->
            val o = HashMap<String, Any?>()
            val a = g.n0("area")
            if (a > 0) {
                val ppl = if (g.n("people") >= 0) g.n("people") else if (s.n0("m2pp") > 0) ceil(a / s.n0("m2pp")) else 0.0
                val or = g.s("orient")
                val wall = s.n0("uWall") * g.n0("wall") * etd(or)
                val glassC = s.n0("uGlass") * g.n0("glass") * dT
                val glassS = g.n0("glass") * shgf(or) * s.n0("sc") * s.n0("clf")
                val roof = s.n0("uRoof") * g.n0("roof") * roofEtd
                val light = g.n0("light") * a
                val equip = g.n0("equip") * a
                val pS = ppl * s.n0("pplS"); val pL = ppl * s.n0("pplL")
                val oa = if (g.n("oa") >= 0) g.n("oa") else s.n0("rp") * ppl + s.n0("ra") * a
                val oaS = 1.23 * oa * dT; val oaL = 3010 * oa * dW
                val rsh = (wall + glassC + glassS + roof + light + equip + pS) * sf
                val rlh = pL * sf
                val total = rsh + rlh + (oaS + oaL) * sf
                val sa = if (dTs > 0) rsh / (1.23 * dTs) else Double.NaN
                o["people"] = ppl; o["oa"] = oa; o["wall"] = wall; o["glass"] = glassC + glassS; o["roof"] = roof; o["internal"] = light + equip + pS
                o["rsh"] = rsh; o["rlh"] = rlh; o["oaLoad"] = (oaS + oaL) * sf; o["total"] = total
                o["kw"] = total / 1000; o["tr"] = total / 1000 / TR_KW; o["sa"] = sa; o["cfm"] = sa * LS_CFM
                o["wm2"] = total / a; o["m2tr"] = if (total > 0) a / (total / 1000 / TR_KW) else Double.NaN
                tA += a; tRsh += rsh; tRlh += rlh; tOa += oa; tOaL += (oaS + oaL) * sf; tTot += total; tSa += if (sa.isNaN()) 0.0 else sa; tPpl += ppl
            }
            r.rows.add(o)
        }
        r["area"] = tA; r["people"] = tPpl; r["rsh"] = tRsh; r["rlh"] = tRlh; r["oa"] = tOa; r["oaLoad"] = tOaL
        r["total"] = tTot; r["kw"] = tTot / 1000; r["tr"] = tTot / 1000 / TR_KW; r["sa"] = tSa; r["cfm"] = tSa * LS_CFM
        r["shr"] = if (tRsh + tRlh > 0) tRsh / (tRsh + tRlh) else Double.NaN
        r["m2tr"] = if (tTot > 0) tA / (tTot / 1000 / TR_KW) else Double.NaN
        r["wm2"] = if (tA > 0) tTot / tA else Double.NaN
        r["dTs"] = dTs
        return r
    }

    /* ------------------------- fresh air (ASHRAE 62.1) ------------------------- */
    class Occ(val name: String, val rp: Double, val ra: Double, val density: Double)
    // Table 6-1 (SI): Rp L/s·person, Ra L/s·m², default occupant density #/100 m²
    val OCC = listOf(
        Occ("Office space", 2.5, 0.3, 5.0),
        Occ("Reception area", 2.5, 0.3, 30.0),
        Occ("Conference / meeting", 2.5, 0.3, 50.0),
        Occ("Corridor / lobby (office)", 0.0, 0.3, 0.0),
        Occ("Hotel bedroom", 2.5, 0.3, 10.0),
        Occ("Hotel lobby", 3.8, 0.3, 30.0),
        Occ("Residential living (approx.)", 2.5, 0.3, 5.0),
        Occ("Classroom (age 9+)", 3.8, 0.6, 35.0),
        Occ("Lecture classroom", 3.8, 0.3, 65.0),
        Occ("Library", 2.5, 0.6, 10.0),
        Occ("Restaurant dining", 3.8, 0.9, 70.0),
        Occ("Cafeteria / fast-food dining", 3.8, 0.9, 100.0),
        Occ("Retail sales", 3.8, 0.6, 15.0),
        Occ("Mall common area", 3.8, 0.3, 40.0),
        Occ("Bank / bank lobby", 3.8, 0.3, 15.0),
        Occ("Barber shop", 3.8, 0.3, 25.0),
        Occ("Beauty / nail salon", 10.0, 0.6, 25.0),
        Occ("Gym – weight room", 10.0, 0.3, 10.0),
        Occ("Gym – aerobics / exercise", 10.0, 0.3, 40.0),
        Occ("Place of worship", 2.5, 0.3, 120.0),
        Occ("Computer lab", 5.0, 0.6, 25.0)
    )
    fun occ(name: String) = OCC.firstOrNull { it.name == name }

    class Exh(val name: String, val rate: Double, val basis: String) // basis: unit | m2 | ach
    // Table 6-4 (SI), continuous rates where a choice exists
    val EXH = listOf(
        Exh("Toilet – public (per WC / urinal)", 25.0, "unit"),
        Exh("Toilet – private (per WC)", 12.5, "unit"),
        Exh("Shower room (per showerhead)", 25.0, "unit"),
        Exh("Kitchen – commercial", 3.5, "m2"),
        Exh("Kitchenette", 1.5, "m2"),
        Exh("Parking garage", 3.7, "m2"),
        Exh("Janitor / trash room", 5.0, "m2"),
        Exh("Locker / dressing room", 2.5, "m2"),
        Exh("By air changes per hour (ACH)", 0.0, "ach")
    )

    fun freshAir(s: ToolState): Res {
        val r = Res()
        var tA = 0.0; var tP = 0.0; var tV = 0.0
        s.list("spaces").forEach { g ->
            val o = HashMap<String, Any?>()
            val c = occ(g.s("cat")); val a = g.n0("area")
            if (c != null && a > 0) {
                val p = if (g.n("people") >= 0) g.n("people") else ceil(c.density * a / 100 - 1e-9)
                val ez = g.n("ez").let { if (it > 0) it else 1.0 }
                val vbz = c.rp * p + c.ra * a
                val voz = vbz / ez
                o["rp"] = c.rp; o["ra"] = c.ra; o["p"] = p; o["vbz"] = vbz; o["voz"] = voz; o["cfm"] = voz * LS_CFM; o["perM2"] = voz / a
                tA += a; tP += p; tV += voz
            }
            r.rows.add(o)
        }
        r["area"] = tA; r["people"] = tP; r["oa"] = tV; r["oaCfm"] = tV * LS_CFM
        var tE = 0.0
        s.list("exhaust").forEach { g ->
            val o = HashMap<String, Any?>()
            val e = EXH.firstOrNull { it.name == g.s("type") }
            if (e != null) {
                val vol = g.n0("area") * g.n0("height")
                val ls = when (e.basis) {
                    "unit" -> e.rate * g.n0("qty")
                    "m2" -> e.rate * g.n0("area")
                    else -> vol * g.n0("ach") / 3.6
                }
                o["rate"] = if (e.basis == "ach") "${toText(g.n0("ach"))} ACH" else toText(e.rate) + if (e.basis == "unit") " L/s each" else " L/s·m²"
                o["ls"] = ls; o["cfm"] = ls * LS_CFM
                o["ach"] = if (vol > 0) ls * 3.6 / vol else Double.NaN
                tE += ls
            }
            r.list("ex").add(o)
        }
        r["exhaust"] = tE; r["exhaustCfm"] = tE * LS_CFM
        r["balance"] = tV - tE
        r["balanceTxt"] = if (tV - tE >= 0) "Positive (fresh air > exhaust)" else "Negative – exhaust exceeds fresh air"
        return r
    }

    /* ------------------------------ ducts ------------------------------ */
    private fun frictionPaM(dM: Double, v: Double, rho: Double, eps: Double, nu: Double): Double {
        if (dM <= 0 || v <= 0) return 0.0
        val re = v * dM / nu
        val f1 = 0.11 * (eps / dM + 68 / re).pow(0.25)
        val f = if (f1 >= 0.018) f1 else 0.85 * f1 + 0.0028
        return f / dM * rho * v * v / 2
    }
    fun roundFor(qM3s: Double, target: Double, rho: Double, eps: Double, nu: Double): Double {
        var lo = 0.02; var hi = 4.0
        repeat(80) {
            val mid = (lo + hi) / 2
            val v = qM3s / (PI * mid * mid / 4)
            if (frictionPaM(mid, v, rho, eps, nu) > target) lo = mid else hi = mid
        }
        return hi
    }
    fun deRect(a: Double, b: Double) = 1.30 * (a * b).pow(0.625) / (a + b).pow(0.25)
    private fun up(x: Double, step: Double) = ceil(x / step - 1e-9) * step

    fun ducts(s: ToolState): Res {
        val r = Res()
        val rho = s.n("rho").let { if (it > 0) it else 1.2 }
        val eps = s.n("eps").let { if (it > 0) it / 1000 else 0.00009 }
        val nu = 1.5e-5
        val target = s.n("rTarget").let { if (it > 0) it else 1.0 }
        val step = s.n("step").let { if (it > 0) it else 50.0 }
        val depthMax = s.n("depth").let { if (it > 0) it else 300.0 }
        var crit = 0.0; var bad = 0
        s.list("seg").forEach { g ->
            val o = HashMap<String, Any?>()
            val q = g.n0("q") / 1000
            if (q > 0) {
                val dCalc = roundFor(q, target, rho, eps, nu) * 1000
                val dStd = up(dCalc, step)
                o["dCalc"] = dCalc; o["dStd"] = dStd
                // rectangular equal-friction equivalent with limited depth
                val hLim = g.n("depth").let { if (it > 0) it else depthMax }
                var h = hLim; var w: Double
                val sq = dCalc / 1.093
                if (sq <= h) { h = up(sq, step); w = h } else {
                    var lo = h; var hi = 20000.0
                    repeat(80) { val mid = (lo + hi) / 2; if (deRect(mid, h) < dCalc) lo = mid else hi = mid }
                    w = up(hi, step)
                }
                val isRound = g.s("shape") == "Round"
                val area = if (isRound) PI * (dStd / 1000).pow(2) / 4 else (w / 1000) * (h / 1000)
                val de = if (isRound) dStd / 1000 else deRect(w, h) / 1000
                val v = q / area
                val rr = frictionPaM(de, v, rho, eps, nu)
                val pv = rho * v * v / 2
                val fr = rr * g.n0("len"); val fit = g.n0("k") * pv
                o["size"] = if (isRound) "Ø ${toText(dStd)}" else "${toText(w)} × ${toText(h)}"
                o["v"] = v; o["r"] = rr; o["pv"] = pv; o["fr"] = fr; o["fit"] = fit; o["tot"] = fr + fit
                val vmax = when (g.s("type")) { "Main" -> s.n0("vMain"); "Branch" -> s.n0("vBranch"); else -> s.n0("vRun") }
                val ar = if (isRound) 1.0 else max(w, h) / min1(w, h)
                o["check"] = when {
                    vmax > 0 && v > vmax -> "TOO HIGH velocity"
                    ar > 4 -> "Aspect > 4 – increase depth"
                    else -> "OK"
                }
                if (o["check"] != "OK") bad++
                if (g.s("crit") == "Yes") crit += fr + fit
            }
            r.rows.add(o)
        }
        r["crit"] = crit; r["bad"] = bad
        val extra = s.n0("pTerm") + s.n0("pRet") + s.n0("pDev")
        val esp = (crit + extra) * (1 + s.n0("safety") / 100)
        r["extra"] = extra; r["esp"] = esp; r["espIn"] = esp / 249.089
        return r
    }
    private fun min1(a: Double, b: Double) = if (a < b) a else b

    fun sqrtSafe(x: Double) = if (x > 0) sqrt(x) else 0.0
}
