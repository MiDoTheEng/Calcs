package com.calcs.app.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* Plumbing + fire fighting calculations. Mirrors the Excel calculators. */
object Eng {
    const val GPM_LS = 0.0630902
    const val GPM_LMIN = 3.78541
    const val G = 9.80665
    const val BAR_M = 100 / G

    fun hw(qM3s: Double, c: Double, dM: Double) = 10.67 * qM3s.pow(1.852) / (c.pow(1.852) * dM.pow(4.87))
    fun areaM2(dmm: Double) = PI * (dmm / 1000).pow(2) / 4
    private fun firstAtLeast(list: List<Double>, x: Double): Int = list.indexOfFirst { it >= x - 1e-9 }

    /* ------------------------------ tables ------------------------------ */
    class Wsfu(val fixture: String, val occ: String, val control: String, val cold: Double, val hot: Double, val total: Double)
    val WSFU = listOf(
        Wsfu("Bathroom group", "Private", "Flush tank", 2.7, 1.5, 3.6),
        Wsfu("Bathroom group", "Private", "Flush valve", 6.0, 3.0, 8.0),
        Wsfu("Bathtub", "Private", "Faucet", 1.0, 1.0, 1.4),
        Wsfu("Bathtub", "Public", "Faucet", 3.0, 3.0, 4.0),
        Wsfu("Bidet", "Private", "Faucet", 1.5, 1.5, 2.0),
        Wsfu("Combination fixture", "Private", "Faucet", 2.25, 2.25, 3.0),
        Wsfu("Dishwashing machine", "Private", "Automatic", 0.0, 1.4, 1.4),
        Wsfu("Drinking fountain", "Offices, etc.", "3/8\" valve", 0.25, 0.0, 0.25),
        Wsfu("Kitchen sink", "Private", "Faucet", 1.0, 1.0, 1.4),
        Wsfu("Kitchen sink", "Hotel, restaurant", "Faucet", 3.0, 3.0, 4.0),
        Wsfu("Laundry trays (1 to 3)", "Private", "Faucet", 1.0, 1.0, 1.4),
        Wsfu("Lavatory", "Private", "Faucet", 0.5, 0.5, 0.7),
        Wsfu("Lavatory", "Public", "Faucet", 1.5, 1.5, 2.0),
        Wsfu("Service sink", "Offices, etc.", "Faucet", 2.25, 2.25, 3.0),
        Wsfu("Shower head", "Public", "Mixing valve", 3.0, 3.0, 4.0),
        Wsfu("Shower head", "Private", "Mixing valve", 1.0, 1.0, 1.4),
        Wsfu("Urinal", "Public", "1\" flush valve", 10.0, 0.0, 10.0),
        Wsfu("Urinal", "Public", "3/4\" flush valve", 5.0, 0.0, 5.0),
        Wsfu("Urinal", "Public", "Flush tank", 3.0, 0.0, 3.0),
        Wsfu("Washing machine (8 lb)", "Private", "Automatic", 1.0, 1.0, 1.4),
        Wsfu("Washing machine (8 lb)", "Public", "Automatic", 2.25, 2.25, 3.0),
        Wsfu("Washing machine (15 lb)", "Public", "Automatic", 3.0, 3.0, 4.0),
        Wsfu("Water closet", "Private", "Flush valve", 6.0, 0.0, 6.0),
        Wsfu("Water closet", "Private", "Flush tank", 2.2, 0.0, 2.2),
        Wsfu("Water closet", "Public", "Flush valve", 10.0, 0.0, 10.0),
        Wsfu("Water closet", "Public", "Flush tank", 5.0, 0.0, 5.0),
        Wsfu("Water closet", "Public or private", "Flushometer tank", 2.0, 0.0, 2.0)
    )
    // IPC E103.3(3): WSFU, flush tank gpm, flush valve gpm (NaN = n/a)
    private val N = Double.NaN
    val HUNTER: List<DoubleArray> = listOf(
        doubleArrayOf(1.0, 3.0, N), doubleArrayOf(2.0, 5.0, N), doubleArrayOf(3.0, 6.5, N), doubleArrayOf(4.0, 8.0, N), doubleArrayOf(5.0, 9.4, 15.0),
        doubleArrayOf(6.0, 10.7, 17.4), doubleArrayOf(7.0, 11.8, 19.8), doubleArrayOf(8.0, 12.8, 22.2), doubleArrayOf(9.0, 13.7, 24.6), doubleArrayOf(10.0, 14.6, 27.0),
        doubleArrayOf(11.0, 15.4, 27.8), doubleArrayOf(12.0, 16.0, 28.6), doubleArrayOf(13.0, 16.5, 29.4), doubleArrayOf(14.0, 17.0, 30.2), doubleArrayOf(15.0, 17.5, 31.0),
        doubleArrayOf(16.0, 18.0, 31.8), doubleArrayOf(17.0, 18.4, 32.6), doubleArrayOf(18.0, 18.8, 33.4), doubleArrayOf(19.0, 19.2, 34.2), doubleArrayOf(20.0, 19.6, 35.0),
        doubleArrayOf(25.0, 21.5, 38.0), doubleArrayOf(30.0, 23.3, 42.0), doubleArrayOf(35.0, 24.9, 44.0), doubleArrayOf(40.0, 26.3, 46.0), doubleArrayOf(45.0, 27.7, 48.0),
        doubleArrayOf(50.0, 29.1, 50.0), doubleArrayOf(60.0, 32.0, 54.0), doubleArrayOf(70.0, 35.0, 58.0), doubleArrayOf(80.0, 38.0, 61.2), doubleArrayOf(90.0, 41.0, 64.3),
        doubleArrayOf(100.0, 43.5, 67.5), doubleArrayOf(120.0, 48.0, 73.1), doubleArrayOf(140.0, 52.5, 77.6), doubleArrayOf(160.0, 57.0, 81.6), doubleArrayOf(180.0, 61.0, 85.6),
        doubleArrayOf(200.0, 65.0, 89.6), doubleArrayOf(225.0, 70.0, 94.6), doubleArrayOf(250.0, 75.0, 99.6), doubleArrayOf(275.0, 80.0, 104.6), doubleArrayOf(300.0, 85.0, 108.6),
        doubleArrayOf(400.0, 105.0, 127.6), doubleArrayOf(500.0, 124.0, 144.3), doubleArrayOf(750.0, 170.0, 180.3), doubleArrayOf(1000.0, 208.0, 208.0), doubleArrayOf(1250.0, 239.0, 239.0),
        doubleArrayOf(1500.0, 269.0, 269.0), doubleArrayOf(1750.0, 297.0, 297.0), doubleArrayOf(2000.0, 325.0, 325.0), doubleArrayOf(2500.0, 380.0, 380.0), doubleArrayOf(3000.0, 433.0, 433.0),
        doubleArrayOf(4000.0, 525.0, 525.0), doubleArrayOf(5000.0, 593.0, 593.0)
    )

    fun hunter(wsfu: Double, flushValve: Boolean): Double {
        if (!(wsfu > 0)) return 0.0
        if (wsfu > 5000) return Double.NaN
        val col = if (flushValve) 2 else 1
        val x = if (flushValve) max(wsfu, 5.0) else wsfu
        if (x < 1) return x * HUNTER[0][1]
        var i = 0
        for (k in HUNTER.indices) if (HUNTER[k][0] <= x) i = k
        val a = HUNTER[i]
        val b = HUNTER.getOrNull(i + 1) ?: return a[col]
        if (a[col].isNaN() || b[col].isNaN()) return a[col]
        return a[col] + (x - a[0]) * (b[col] - a[col]) / (b[0] - a[0])
    }

    val HOT_FIXTURES = listOf("Lavatory - private" to 2.0, "Lavatory - public" to 4.0, "Bathtub" to 20.0, "Shower" to 30.0, "Kitchen sink" to 10.0,
        "Dishwasher" to 15.0, "Laundry tub / washing machine" to 20.0, "Pantry sink" to 5.0, "Service sink" to 20.0)

    class Dfu(val fixture: String, val occ: String, val dfu: Double, val system: String)
    val DFU = listOf(
        Dfu("Water closet, 1.6 gpf or less", "Private", 3.0, "Soil"),
        Dfu("Water closet, more than 1.6 gpf", "Private", 4.0, "Soil"),
        Dfu("Water closet, 1.6 gpf or less", "Public", 4.0, "Soil"),
        Dfu("Water closet, more than 1.6 gpf", "Public", 6.0, "Soil"),
        Dfu("Urinal", "-", 4.0, "Soil"),
        Dfu("Urinal, 1 gallon or less", "-", 2.0, "Soil"),
        Dfu("Bathroom group, WC 1.6 gpf or less", "Private", 5.0, "Combined only"),
        Dfu("Bathroom group, WC more than 1.6 gpf", "Private", 6.0, "Combined only"),
        Dfu("Lavatory", "-", 1.0, "Waste"),
        Dfu("Shower", "-", 2.0, "Waste"),
        Dfu("Bathtub", "-", 2.0, "Waste"),
        Dfu("Bidet", "-", 1.0, "Waste"),
        Dfu("Kitchen sink, domestic", "Private", 2.0, "Waste"),
        Dfu("Dishwasher, domestic", "Private", 2.0, "Waste"),
        Dfu("Clothes washer, residential", "Private", 2.0, "Waste"),
        Dfu("Clothes washer, commercial", "Public", 3.0, "Waste"),
        Dfu("Laundry tray", "-", 2.0, "Waste"),
        Dfu("Service sink", "-", 2.0, "Waste"),
        Dfu("Floor drain", "-", 2.0, "Waste"),
        Dfu("Drinking fountain", "-", 0.5, "Waste")
    )
    val DRAIN_SIZES = listOf(1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0)
    val DRAIN_HB = listOf(3.0, 6.0, 12.0, 20.0, 160.0, 360.0, 620.0, 1400.0)
    val DRAIN_ST3 = listOf(4.0, 10.0, 20.0, 48.0, 240.0, 540.0, 960.0, 2200.0)
    val DRAIN_STT = listOf(8.0, 24.0, 42.0, 72.0, 500.0, 1100.0, 1900.0, 3600.0)
    val DRAIN_STI = listOf(2.0, 6.0, 9.0, 20.0, 90.0, 200.0, 350.0, 600.0)
    val DRAIN_DN = listOf(40, 50, 65, 80, 100, 125, 150, 200)
    val BD_SIZES = listOf(3.0, 4.0, 5.0, 6.0, 8.0)
    val BD = mapOf(1 to listOf(36.0, 180.0, 390.0, 700.0, 1600.0), 2 to listOf(42.0, 216.0, 480.0, 840.0, 1920.0), 4 to listOf(50.0, 250.0, 575.0, 1000.0, 2300.0))
    val VENTS = listOf(1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0)
    fun dnOf(inch: Double): Int? {
        val i = DRAIN_SIZES.indexOf(inch)
        if (i >= 0) return DRAIN_DN[i]
        return mapOf(1.25 to 32, 10.0 to 250, 12.0 to 300)[inch]
    }

    // IPC 1106.2: size (in), capacity (gpm), DN
    val LEADERS = listOf(Triple(2.0, 30.0, 50), Triple(2.5, 54.0, 65), Triple(3.0, 92.0, 80), Triple(4.0, 192.0, 100), Triple(5.0, 360.0, 125), Triple(6.0, 563.0, 150), Triple(8.0, 1208.0, 200))
    val STORM_PIPES = listOf(75.0, 100.0, 150.0, 200.0, 250.0, 300.0, 375.0, 450.0)

    class Hazard(val name: String, val examples: String, val density: Double, val area: Double, val hose: Double, val durMin: Double, val durMax: Double, val maxCov: Double)
    val HAZARDS = listOf(
        Hazard("Light", "Residential, offices, hospitals, schools", 0.10, 1500.0, 100.0, 30.0, 30.0, 225.0),
        Hazard("Ordinary Group 1", "Car parks, restaurant service areas, laundries", 0.15, 1500.0, 250.0, 60.0, 90.0, 130.0),
        Hazard("Ordinary Group 2", "Shops, light storage, workshops", 0.20, 1500.0, 250.0, 60.0, 90.0, 130.0),
        Hazard("Extra Group 1", "Hazardous processes", 0.30, 2500.0, 500.0, 90.0, 120.0, 100.0),
        Hazard("Extra Group 2", "Flammable liquids processes", 0.40, 2500.0, 500.0, 90.0, 120.0, 100.0)
    )
    const val DENS_SI = 40.746
    const val FT2_M2 = 0.092903
    fun hazard(name: String): Hazard? = HAZARDS.firstOrNull { it.name == name }
    val SCH40 = listOf("1\"" to 26.6, "1¼\"" to 35.1, "1½\"" to 40.9, "2\"" to 52.5, "2½\"" to 62.7, "3\"" to 77.9, "4\"" to 102.3, "6\"" to 154.1)

    /* ---------------------------- water supply ---------------------------- */
    fun wsBuilding(s: ToolState): Res {
        Rooms.apply(s, "rooms", "fix", WSFU.size, "unit", "extra")
        val r = Res()
        val units = s.n0("units")
        val persons = units * s.n0("persons")
        val daily = persons * s.n0("lpd") / 1000
        r["persons"] = persons; r["daily"] = daily; r["tank"] = daily * s.n0("days")
        var tq = 0.0; var tw = 0.0; var tc = 0.0; var th = 0.0; var fv = false
        val fix = s.list("fix")
        WSFU.forEachIndexed { i, f ->
            val row = fix.getOrNull(i)
            val q = (row?.n0("unit") ?: 0.0) * units + (row?.n0("extra") ?: 0.0)
            if (q > 0 && f.control.contains("flush valve", true)) fv = true
            r.rows.add(hashMapOf("qty" to q, "wsfu" to f.total * q, "cold" to f.cold * q, "hot" to f.hot * q))
            tq += q; tw += f.total * q; tc += f.cold * q; th += f.hot * q
        }
        r["lQty"] = tq; r["lWsfu"] = tw; r["lCold"] = tc; r["lHot"] = th
        s.list("other").forEach { o ->
            val q = o.n0("unit") * units + o.n0("extra")
            val each = o.n0("wsfu")
            val hot = o.s("hot") == "yes"
            val c = if (hot) each * 0.75 else each; val h = if (hot) each * 0.75 else 0.0
            if (q > 0 && o.s("fv") == "yes") fv = true
            r.list("other").add(hashMapOf("qty" to q, "wsfu" to each * q, "cold" to c * q, "hot" to h * q))
            tq += q; tw += each * q; tc += c * q; th += h * q
        }
        r["totQty"] = tq; r["totWsfu"] = tw; r["totCold"] = tc; r["totHot"] = th
        r["curve"] = if (fv) "Flush valve" else "Flush tank"
        val gpm = hunter(tw, fv)
        val ls = gpm * GPM_LS
        r["gpm"] = if (gpm.isNaN()) "Above table" else gpm
        r["ls"] = ls
        r["reqID"] = sqrt(4 * (ls / 1000) / (PI * s.n0("vTarget"))) * 1000
        val id = s.n("id")
        val v = (ls / 1000) / areaM2(id)
        r["v"] = v
        r["vCheck"] = if (v <= s.n0("vMax")) "OK" else "TOO HIGH – increase pipe size"
        val le = if (s.n("feq") > 0) s.n0("len") + s.n("feq") else s.n0("len") * (1 + s.n0("fpct") / 100)
        val j = hw(ls / 1000, s.n0("C"), id / 1000)
        val hf = j * le
        r["Le"] = le; r["j"] = j; r["hf"] = hf
        val resM = s.n0("resBar") * BAR_M
        val sub = s.n0("stat") + resM + hf
        val head = sub * (1 + s.n0("safety") / 100)
        val hyd = 1000 * G * (ls / 1000) * head / 1000
        r["resM"] = resM; r["sub"] = sub; r["head"] = head; r["m3h"] = ls * 3.6; r["hyd"] = hyd
        r["shaft"] = hyd / (s.n0("eff") / 100)
        return r
    }

    fun wsSegments(s: ToolState): Res {
        val r = Res(); var tot = 0.0
        s.list("seg").forEach { g ->
            val o = HashMap<String, Any?>()
            val gpm = g.n("gpm")
            if (gpm > 0) {
                val ls = gpm * GPM_LS; o["ls"] = ls
                if (g.n("vt") > 0) o["req"] = sqrt(4 * (ls / 1000) / (PI * g.n("vt"))) * 1000
                val id = g.n("id")
                if (id > 0) {
                    val v = (ls / 1000) / areaM2(id); o["v"] = v
                    val req = o["req"] as Double?
                    o["check"] = if (req != null && id < req) "ID TOO SMALL" else if (v > s.n0("vMax")) "TOO FAST" else "OK"
                    o["j"] = hw(ls / 1000, s.n0("C"), id / 1000)
                }
                if (g.n("len") > 0) o["Le"] = g.n("len") * (1 + g.n0("fpct") / 100)
                val j = o["j"] as Double?; val le = o["Le"] as Double?
                if (j != null && le != null) { o["loss"] = j * le; tot += j * le }
            }
            r.rows.add(o)
        }
        r["total"] = tot
        r["bad"] = r.rows.count { (it["check"] as String?)?.let { c -> c != "OK" } ?: false }
        return r
    }

    fun wsHot(s: ToolState): Res {
        Rooms.apply(s, "rooms", "qty", HOT_FIXTURES.size, "q", null)
        val r = Res(); var possible = 0.0
        val q = s.list("qty")
        HOT_FIXTURES.forEachIndexed { i, f ->
            val n = q.getOrNull(i)?.n0("q") ?: 0.0
            val lh = f.second * GPM_LMIN
            r.rows.add(hashMapOf("lh" to lh, "total" to lh * n))
            possible += lh * n
        }
        r["lPossible"] = possible
        s.list("other").forEach { o ->
            val lh = o.n0("lh"); val tot = lh * o.n0("q")
            r.list("other").add(hashMapOf("total" to tot)); possible += tot
        }
        val pmd = possible * s.n0("df")
        val storage = pmd * s.n0("sf")
        val dT = s.n0("ts") - s.n0("tc")
        val vol = if (s.n("vol") > 0) s.n("vol") else storage
        r["possible"] = possible; r["pmd"] = pmd; r["storage"] = storage; r["dT"] = dT; r["vol"] = vol
        r["pTank"] = vol * 4.186 * dT / (3600 * s.n0("rec"))
        r["pCont"] = pmd * 4.186 * dT / 3600
        val daily = s.n0("persons") * s.n0("lppd")
        r["daily"] = daily
        r["pct"] = if (daily > 0) storage / daily * 100 else 0.0
        return r
    }

    /* ------------------------------ drainage ------------------------------ */
    fun drFixtures(s: ToolState): Res {
        Rooms.apply(s, "rooms", "fix", DFU.size, "unit", "extra")
        val r = Res()
        val units = s.n0("units")
        var t = 0.0; var so = 0.0; var w = 0.0; var us = 0.0; var uw = 0.0; var comb = false
        val fix = s.list("fix")
        DFU.forEachIndexed { i, f ->
            val row = fix.getOrNull(i)
            val per = row?.n0("unit") ?: 0.0
            val q = per * units + (row?.n0("extra") ?: 0.0)
            val d = f.dfu * q
            if (q > 0 && f.system == "Combined only") comb = true
            r.rows.add(hashMapOf("qty" to q, "dfu" to d))
            t += d
            if (f.system == "Soil") { so += d; us += f.dfu * per }
            if (f.system == "Waste") { w += d; uw += f.dfu * per }
        }
        r["lTotal"] = t
        s.list("other").forEach { o ->
            val per = o.n0("unit"); val q = per * units + o.n0("extra"); val d = o.n0("dfu") * q
            r.list("other").add(hashMapOf("qty" to q, "dfu" to d))
            t += d
            if (o.s("sys") == "Soil") { so += d; us += o.n0("dfu") * per } else { w += d; uw += o.n0("dfu") * per }
        }
        r["total"] = t; r["soil"] = so; r["waste"] = w; r["unitSoil"] = us; r["unitWaste"] = uw
        r["warning"] = if (comb) "Bathroom groups entered: combined (one-pipe) system only" else null
        return r
    }

    fun drainSize(g: RowData): HashMap<String, Any?> {
        val o = HashMap<String, Any?>()
        val dfu = g.n("dfu")
        if (!(dfu > 0)) return o
        val type = g.s("type")
        val size: Double = when (type) {
            "Stack" -> if (g.n0("intervals") <= 3) {
                firstAtLeast(DRAIN_ST3, dfu).let { if (it < 0) 99.0 else DRAIN_SIZES[it] }
            } else {
                val a = firstAtLeast(DRAIN_STT, dfu); val b = firstAtLeast(DRAIN_STI, g.n0("atInterval"))
                max(if (a < 0) 99.0 else DRAIN_SIZES[a], if (b < 0) 99.0 else DRAIN_SIZES[b])
            }
            "Building drain" -> {
                val col = BD[g.n("slope").let { if (it.isNaN()) -1 else it.roundToInt() }]
                if (col == null) { o["required"] = "Pick slope 1, 2 or 4 %"; return o }
                firstAtLeast(col, dfu).let { if (it < 0) 99.0 else BD_SIZES[it] }
            }
            else -> firstAtLeast(DRAIN_HB, dfu).let { if (it < 0) 99.0 else DRAIN_SIZES[it] }
        }
        if (size == 99.0) { o["table"] = "Above table"; o["required"] = "Above table"; return o }
        o["table"] = size
        val req = if (type == "Building drain") max(size, 3.0) else max(size, if (g.s("wc") == "Yes") 3.0 else 0.0)
        o["required"] = req
        o["dn"] = dnOf(req)?.toDouble()
        val sel = g.n("selected")
        if (sel > 0) o["check"] = if (sel >= req) (if (sel > req) "OK (larger than required)" else "OK") else "TOO SMALL"
        val basis = if (sel > 0) sel else req
        val vi = firstAtLeast(VENTS, max(1.25, basis / 2))
        o["vent"] = if (vi < 0) "Above table" else VENTS[vi]
        return o
    }

    fun drPipes(s: ToolState): Res {
        val r = Res()
        s.list("seg").forEach { r.rows.add(drainSize(it)) }
        r["bad"] = r.rows.count { it["check"] == "TOO SMALL" || it["required"] == "Above table" }
        return r
    }

    /* ------------------------------ rainwater ----------------------------- */
    fun rnLeaders(s: ToolState): Res {
        val r = Res()
        val i = s.n0("intensity"); val minL = s.n0("minLeader")
        var a = 0.0; var q = 0.0
        s.list("areas").forEach { g ->
            val o = HashMap<String, Any?>()
            val area = g.n("area")
            if (area > 0) {
                val ls = g.n0("c") * i * area / 3600
                o["ls"] = ls; o["gpm"] = ls / GPM_LS
                a += area; q += ls
                if (g.n("n") > 0) {
                    val per = ls / GPM_LS / g.n("n"); o["per"] = per
                    val k = LEADERS.indexOfFirst { it.second >= per - 1e-9 }
                    if (k < 0) { o["table"] = "Above table"; o["required"] = "Above table" } else {
                        o["table"] = LEADERS[k].first
                        val req = max(LEADERS[k].first, minL)
                        o["required"] = req
                        o["dn"] = LEADERS.firstOrNull { it.first == req }?.third?.toDouble()
                        val sel = g.n("selected")
                        if (sel > 0) o["check"] = if (sel >= req) "OK" else "TOO SMALL"
                    }
                }
            }
            r.rows.add(o)
        }
        r["area"] = a; r["ls"] = q; r["gpm"] = q / GPM_LS
        return r
    }

    fun rnDrains(s: ToolState): Res {
        val r = Res()
        s.list("seg").forEach { g ->
            val o = HashMap<String, Any?>()
            val q = g.n("q"); val sl = g.n("slope"); val n = g.n("n")
            if (q > 0 && sl > 0 && n > 0) {
                val req = ((q / 1000) * n * 4.0.pow(5.0 / 3) / (PI * sqrt(sl / 100))).pow(3.0 / 8) * 1000
                o["req"] = req
                val k = firstAtLeast(STORM_PIPES, req)
                if (k < 0) o["nominal"] = "Above list" else {
                    o["nominal"] = STORM_PIPES[k]
                    val d = STORM_PIPES[k] / 1000
                    val cap = (1 / n) * (PI * d * d / 4) * (d / 4).pow(2.0 / 3) * sqrt(sl / 100) * 1000
                    o["cap"] = cap; o["v"] = (cap / 1000) / (PI * d * d / 4)
                    o["check"] = if (cap >= q) "OK" else "TOO SMALL"
                }
            }
            r.rows.add(o)
        }
        r["bad"] = r.rows.count { it["check"] == "TOO SMALL" || it["nominal"] == "Above list" }
        return r
    }

    /* -------------------------------- fire -------------------------------- */
    fun riserDemand(n: Double, sprinklered: String) = min(500 + 250 * (max(1.0, n) - 1), if (sprinklered == "No") 1250.0 else 1000.0)

    fun fireDemand(s: ToolState): Res {
        val r = Res()
        val h = hazard(s.s("hazard")) ?: HAZARDS[0]
        r["examples"] = h.examples
        val dens = h.density * DENS_SI; val area = h.area * FT2_M2
        r["densUS"] = h.density; r["dens"] = dens; r["areaUS"] = h.area; r["area"] = area
        val sprL = dens * area
        r["sprLmin"] = sprL; r["sprLs"] = sprL / 60; r["sprGpm"] = sprL / GPM_LMIN
        val hoseL = h.hose * GPM_LMIN
        r["hoseGpm"] = h.hose; r["hoseLs"] = hoseL / 60
        val tot = sprL + hoseL
        r["totLs"] = tot / 60; r["totGpm"] = tot / GPM_LMIN
        r["durRange"] = "${toText(h.durMin)} – ${toText(h.durMax)}"
        val dur = s.n0("duration")
        r["durCheck"] = if (dur >= h.durMin && dur <= h.durMax) "OK" else "Outside table range"
        r["nfpaTank"] = tot * dur / 1000
        val maxCov = h.maxCov * FT2_M2
        r["maxCov"] = maxCov
        r["covCheck"] = if (s.n0("coverage") <= maxCov) "OK" else "TOO LARGE"
        val qMin = dens * s.n0("coverage")
        val pReq = (qMin / s.n0("K")).pow(2)
        val pDes = max(pReq, s.n0("pMin"))
        r["qMin"] = qMin; r["pReq"] = pReq; r["pDes"] = pDes; r["qDes"] = s.n0("K") * sqrt(pDes)
        r["nSpr"] = ceil(area / s.n0("coverage") - 1e-9)
        val riserGpm = riserDemand(s.n0("risers"), s.s("sprinklered"))
        val riserHead = s.n0("lvElev") + s.n0("lvFric") + s.n0("lvBar") * BAR_M
        val sprHead = s.n0("spElev") + s.n0("spFric") + pDes * BAR_M
        r["riserGpm"] = riserGpm; r["riserHead"] = riserHead; r["sprHead"] = sprHead
        val pumpGpm = max(tot / GPM_LMIN, riserGpm)
        val pumpHead = max(riserHead, sprHead)
        r["pumpGpm"] = pumpGpm; r["pumpLs"] = pumpGpm * GPM_LMIN / 60; r["pumpHead"] = pumpHead; r["pumpBar"] = pumpHead / BAR_M
        val tankMin = max(60.0, dur)
        r["tankMin"] = tankMin; r["tank"] = pumpGpm * GPM_LMIN * tankMin / 1000
        return r
    }

    fun fireZones(s: ToolState): Res {
        val r = Res()
        var maxQ = 0.0; var maxH = 0.0; var maxV = 0.0
        s.list("zones").forEach { z ->
            val o = HashMap<String, Any?>()
            val h = hazard(z.s("hazard"))
            if (h != null) {
                val dens = h.density * DENS_SI
                val spr = h.density * h.area; val tot = spr + h.hose
                o["spr"] = spr; o["hose"] = h.hose; o["tot"] = tot; o["ls"] = tot * GPM_LMIN / 60
                val p = max(0.5, (dens * z.n0("coverage") / z.n0("K")).pow(2))
                o["p"] = p
                if (!z.n("elev").isNaN() && !z.n("fric").isNaN()) { val hd = z.n("elev") + z.n("fric") + p * BAR_M; o["head"] = hd; maxH = max(maxH, hd) }
                if (!z.n("duration").isNaN()) {
                    val vol = tot * GPM_LMIN * z.n("duration") / 1000; o["vol"] = vol; maxV = max(maxV, vol)
                    o["check"] = if (z.n("duration") >= h.durMin && z.n("duration") <= h.durMax) "OK" else "Outside table range"
                }
                maxQ = max(maxQ, tot)
            }
            r.rows.add(o)
        }
        val riserGpm = riserDemand(s.n0("risers"), s.s("sprinklered"))
        val riserHead = s.n0("lvElev") + s.n0("lvFric") + s.n0("lvBar") * BAR_M
        val pumpGpm = max(maxQ, riserGpm); val pumpHead = max(maxH, riserHead)
        r["riserGpm"] = riserGpm; r["riserHead"] = riserHead
        r["pumpGpm"] = pumpGpm; r["pumpLs"] = pumpGpm * GPM_LMIN / 60; r["pumpM3h"] = pumpGpm * GPM_LMIN * 60 / 1000
        r["pumpHead"] = pumpHead; r["pumpBar"] = pumpHead / BAR_M; r["at150"] = 0.65 * pumpHead; r["shutoff"] = 1.4 * pumpHead
        r["tank"] = max(maxV, pumpGpm * GPM_LMIN * 60 / 1000)
        return r
    }

    fun branchCount(s: ToolState) = s.n("n").let { if (it.isNaN()) 1 else it.roundToInt().coerceIn(1, 15) }

    fun fireBranch(s: ToolState): Res {
        val r = Res()
        val k = s.n0("K"); val c = s.n0("C"); val nSpr = branchCount(s)
        var p = s.n0("p1"); var q = 0.0
        val lim = doubleArrayOf(s.n("vGood").let { if (it > 0) it else 3.0 }, s.n("vPractice").let { if (it > 0) it else 6.0 }, s.n("vMax").let { if (it > 0) it else 10.0 })
        val seg = s.list("seg")
        var maxP = 0.0; var maxV = 0.0
        for (i in 0 until nSpr) {
            val g = seg.getOrNull(i)
            val d = g?.n0("d") ?: 0.0; val len = g?.n0("len") ?: 0.0; val le = g?.n0("fit") ?: 0.0; val dz = g?.n0("dz") ?: 0.0
            val qs = k * sqrt(max(p, 0.0)); q += qs
            val pf = 6.05e5 * q.pow(1.85) / (c.pow(1.85) * d.pow(4.87))
            val loss = pf * (len + le)
            val next = p + loss + dz * G / 100
            val v = (q / 60000) / areaM2(d)
            val chk = if (v > lim[2]) "Too high – upsize" else if (v > lim[1]) "Upsize (above practice limit)" else if (v > lim[0]) "OK" else "Good"
            r.rows.add(hashMapOf("P" to p, "q" to qs, "Q" to q, "d" to d, "p" to pf, "loss" to loss, "next" to next, "v" to v, "check" to chk))
            maxP = max(maxP, p); maxV = max(maxV, v)
            p = next
        }
        val last = r.rows.last()
        val qt = last["Q"] as Double; val pin = last["next"] as Double
        r["Q"] = qt; r["Qls"] = qt / 60; r["Qgpm"] = qt / GPM_LMIN; r["Pin"] = pin; r["PinM"] = pin * BAR_M
        val est = s.n0("density") * s.n0("coverage") * nSpr
        r["est"] = est; r["ratio"] = if (est > 0) qt / est * 100 else 0.0
        r["vMax"] = maxV; r["lim"] = lim
        return r
    }

    /* -------------------------------- units -------------------------------- */
    val UNITS: List<Pair<String, List<Pair<String, Double>>>> = listOf(
        "Flow" to listOf("L/s" to 0.001, "m³/s" to 1.0, "m³/h" to 1 / 3600.0, "L/min" to 1 / 60000.0, "US gpm" to 6.30901964e-05, "Imp gpm" to 7.57681667e-05, "CFM" to 0.000471947),
        "Pressure / head" to listOf("kPa" to 1.0, "bar" to 100.0, "psi" to 6.894757293, "m H₂O" to 9.80665, "ft H₂O" to 2.98906692, "Pa" to 0.001, "in. wg" to 0.249089),
        "Length / diameter" to listOf("mm" to 0.001, "cm" to 0.01, "m" to 1.0, "inch" to 0.0254, "ft" to 0.3048),
        "Volume" to listOf("L" to 0.001, "m³" to 1.0, "US gal" to 0.00378541, "Imp gal" to 0.00454609, "ft³" to 0.0283168),
        "Area" to listOf("m²" to 1.0, "ft²" to 0.092903, "cm²" to 0.0001),
        "Power / cooling" to listOf("kW" to 1.0, "W" to 0.001, "TR" to 3.51685, "BTU/h" to 0.000293071, "hp" to 0.7457),
        "Velocity" to listOf("m/s" to 1.0, "fpm" to 0.00508, "ft/s" to 0.3048, "km/h" to 1 / 3.6),
        "Sprinkler density" to listOf("mm/min (L/min/m²)" to 1.0, "gpm/ft²" to 40.746),
        "K-factor" to listOf("L/min/bar^0.5" to 1.0, "gpm/psi^0.5" to 14.40)
    )
}
