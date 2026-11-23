package com.calcs.app.core

/* Fire fighting and HVAC tool screens, plus the branch list. */
object FireTools {
    private val YESNO = opts("Yes", "No")
    private val HAZ = Eng.HAZARDS.map { Opt(it.name, it.name) }
    private val SCH = Eng.SCH40.map { Opt(toText(it.second), "${it.first} (${toText(it.second)})") }

    val demand = ToolDef(
        id = "fire-demand", title = "Fire water demand", sub = "Density/area, hose, one sprinkler, UAE risers → pump & tank",
        defaults = { state("hazard" to "Light", "duration" to 30, "coverage" to 12, "K" to 80.6, "pMin" to 0.5, "risers" to 1, "sprinklered" to "Yes",
            "lvBar" to 6.9, "lvElev" to 15, "lvFric" to 5, "spElev" to 17, "spFric" to 8) },
        compute = Eng::fireDemand,
        tiles = { _, r -> listOf(RRow("Sprinkler flow", r["sprGpm"], "gpm", 0), RRow("Total (spr + hose)", r["totLs"], "L/s", 2),
            RRow("Fire pump", "${fmt(r["pumpGpm"], 0)} gpm @ ${fmt(r["pumpHead"], 1)} m"), RRow("Fire tank (UAE)", r["tank"], "m³", 1)) },
        blocks = listOf(
            Section("1 · Hazard classification", listOf(
                Field("hazard", "Hazard classification", kind = Kind.SELECT, options = HAZ, onChange = { s -> Eng.hazard(s.s("hazard"))?.let { s.set("duration", it.durMin) } })
            ), results = { _, r -> listOf(RRow("Examples", r["examples"]), RRow("Design density", r["densUS"], "gpm/ft²", 2), RRow("Design density", r["dens"], "mm/min", 2),
                RRow("Design area", r["areaUS"], "ft²", 0), RRow("Design area", r["area"], "m²", 1)) }),
            Section("2 · Sprinkler demand, hose & total", emptyList(), results = { _, r -> listOf(
                RRow("Sprinkler flow = density × area", r["sprLmin"], "L/min", 1), RRow("Sprinkler flow", r["sprGpm"], "gpm", 1), RRow("Sprinkler flow", r["sprLs"], "L/s", 2),
                RRow("Hose stream allowance (added)", r["hoseGpm"], "gpm", 0), RRow("Hose stream allowance", r["hoseLs"], "L/s", 2),
                RRow("TOTAL fire water flow", r["totGpm"], "gpm", 1, true), RRow("TOTAL fire water flow", r["totLs"], "L/s", 2, true)) }),
            Section("3 · Fire water storage (NFPA only)", listOf(
                Field("duration", "Selected duration (NFPA table)", "min", "UAE tank uses ≥ 60 min of PUMP flow (section 5)")
            ), results = { _, r -> listOf(RRow("Duration range (table)", r["durRange"], "min"), RRow("Duration check", r["durCheck"]), RRow("Fire water, sprinklers + hose (NFPA)", r["nfpaTank"], "m³", 1)) }),
            Section("4 · One sprinkler: Q = K √P", listOf(
                Field("coverage", "Coverage per sprinkler (as designed)", "m²"),
                Field("K", "K-factor", "L/min/bar½", "K80 = K5.6"),
                Field("pMin", "Minimum operating pressure", "bar", "≈ 7 psi typical")
            ), results = { _, r -> listOf(RRow("Max coverage for this hazard", r["maxCov"], "m²", 1), RRow("Coverage check", r["covCheck"]), RRow("Minimum flow per sprinkler", r["qMin"], "L/min", 1),
                RRow("Required pressure P = (Q/K)²", r["pReq"], "bar", 3), RRow("Design pressure at sprinkler", r["pDes"], "bar", 2, true), RRow("Flow at design pressure", r["qDes"], "L/min", 1),
                RRow("Sprinklers operating in design area", r["nSpr"], "no.", 0)) }, diagram = "ff-sprinkler"),
            Section("5 · UAE wet riser & governing values", listOf(
                Field("risers", "Number of wet risers (Class III)", "no."),
                Field("sprinklered", "Building fully sprinklered?", kind = Kind.SELECT, options = YESNO),
                Field("lvBar", "Residual pressure at remotest 65 mm landing valve", "bar", "6.9 or 7 bar depending on clause"),
                Field("lvElev", "Elevation pump → remotest landing valve", "m"),
                Field("lvFric", "Friction to remotest landing valve", "m"),
                Field("spElev", "Elevation pump → remotest sprinkler", "m"),
                Field("spFric", "Friction to remotest sprinkler", "m")
            ), results = { _, r -> listOf(RRow("Riser demand (500 + 250 / extra riser)", r["riserGpm"], "gpm", 0), RRow("Riser head needed", r["riserHead"], "m", 1),
                RRow("Sprinkler head needed", r["sprHead"], "m", 1), RRow("FIRE PUMP flow (largest)", r["pumpGpm"], "gpm", 0, true), RRow("FIRE PUMP flow", r["pumpLs"], "L/s", 2),
                RRow("FIRE PUMP head (largest)", r["pumpHead"], "m", 1, true), RRow("FIRE PUMP pressure", r["pumpBar"], "bar", 2),
                RRow("Tank duration (UAE ≥ 60 min of pump flow)", r["tankMin"], "min", 0), RRow("FIRE WATER TANK", r["tank"], "m³", 1, true)) }, diagram = "ff-riser"),
            Note("Hose reel: 6.5 gpm at 4.5 bar at the remotest 25 mm reel, max 12 bar, PRV above 7 bar. UAE values from a published copy of the UAE Fire & Life Safety Code Ch. 9 – verify the current edition with Civil Defence. The final design needs a full hydraulic calculation.")
        )
    )

    private fun zone(name: String, hz: String, dur: Any?, elev: Any?, fric: Any?) = row("zone" to name, "hazard" to hz, "duration" to dur, "coverage" to 12, "K" to 80.6, "elev" to elev, "fric" to fric)
    val zones = ToolDef(
        id = "fire-zones", title = "Building zones → pump & tank", sub = "Several hazards in one building",
        defaults = { state("risers" to 1, "sprinklered" to "Yes", "lvBar" to 6.9, "lvElev" to 26, "lvFric" to 10, lists = mapOf("zones" to listOf(
            zone("Car park (basement)", "Ordinary Group 1", 60, "", ""), zone("Retail (ground)", "Ordinary Group 2", 60, 8, 10), zone("Offices (top floor)", "Light", 30, 29, 12)))) },
        blank = { state("risers" to 1, "sprinklered" to "Yes", "lvBar" to 6.9, "lvElev" to "", "lvFric" to "", lists = mapOf("zones" to listOf(zone("Zone 1", "Light", 30, "", "")))) },
        compute = Eng::fireZones,
        tiles = { _, r -> listOf(RRow("Fire pump", "${fmt(r["pumpGpm"], 0)} gpm @ ${fmt(r["pumpHead"], 1)} m"), RRow("Pump pressure", r["pumpBar"], "bar", 2), RRow("Fire tank", r["tank"], "m³", 1)) },
        blocks = listOf(
            Grid("Zones", "zones", titleKey = "zone",
                help = "One row per zone. Leave elevation / friction blank if unknown – that zone then doesn't count for the head.",
                add = { zone("Zone", "Light", 30, "", "") },
                cols = listOf(Field("zone", "Zone", kind = Kind.TEXT, width = 190), Field("hazard", "Hazard", kind = Kind.SELECT, options = HAZ, width = 180),
                    Field("duration", "Duration", "min"), Field("coverage", "Coverage", "m²"), Field("K", "K"), Field("elev", "Elevation", "m"), Field("fric", "Friction", "m")),
                outs = listOf(Out("Spr (gpm)", 0) { r, i -> r.row(i, "spr") }, Out("Hose (gpm)", 0) { r, i -> r.row(i, "hose") },
                    Out("Total (gpm)", 0, key = true) { r, i -> r.row(i, "tot") }, Out("Total (L/s)", 2) { r, i -> r.row(i, "ls") },
                    Out("P last spr (bar)", 2) { r, i -> r.row(i, "p") }, Out("Head (m)", 1, summary = true) { r, i -> r.row(i, "head") },
                    Out("Water (m³)", 1) { r, i -> r.row(i, "vol") }, Out("Duration", summary = true) { r, i -> r.row(i, "check") })),
            Section("Wet riser (UAE)", listOf(
                Field("risers", "Number of wet risers (Class III)", "no."),
                Field("sprinklered", "Building fully sprinklered?", kind = Kind.SELECT, options = YESNO),
                Field("lvBar", "Residual pressure at remotest landing valve", "bar"),
                Field("lvElev", "Elevation pump → remotest landing valve", "m"),
                Field("lvFric", "Friction to remotest landing valve", "m")
            ), results = { _, r -> listOf(RRow("Riser demand", r["riserGpm"], "gpm", 0), RRow("Riser head needed", r["riserHead"], "m", 1), RRow("FIRE PUMP rated flow", r["pumpGpm"], "gpm", 0, true),
                RRow("Fire pump rated flow", r["pumpLs"], "L/s", 2), RRow("Fire pump rated flow", r["pumpM3h"], "m³/h", 1), RRow("FIRE PUMP rated head", r["pumpHead"], "m", 1, true),
                RRow("Fire pump rated pressure", r["pumpBar"], "bar", 2), RRow("NFPA 20: at 150 % flow, at least", r["at150"], "m", 1), RRow("NFPA 20: shut-off head not above", r["shutoff"], "m", 1),
                RRow("FIRE TANK = max(zones, pump × 60 min)", r["tank"], "m³", 1, true)) }, diagram = "ff-riser"),
            Note("Duty point = largest flow AND largest head, even from different zones. Lower zones will see excess pressure → PRVs / zoning. The authority may require extra storage (hose reels, hydrants).")
        )
    )

    val branch = ToolDef(
        id = "fire-branch", title = "Branch hydraulic calc", sub = "Sprinkler by sprinkler, with velocity check & sketch",
        defaults = { state("K" to 80.6, "p1" to 0.5, "C" to 120, "n" to 4, "dDef" to 26.6, "lenDef" to 3.5, "density" to 4.0746, "coverage" to 12,
            "vGood" to 3, "vPractice" to 6, "vMax" to 10, lists = mapOf("seg" to (0 until 15).map { row("d" to 26.6, "len" to 3.5, "fit" to 0, "dz" to 0) })) },
        compute = Eng::fireBranch,
        tiles = { _, r -> listOf(RRow("Total flow", r["Q"], "L/min", 1), RRow("Total flow", r["Qgpm"], "gpm", 1), RRow("Pressure at branch inlet", r["Pin"], "bar", 2),
            RRow("Highest velocity", r["vMax"], "m/s", 2), RRow("Hydraulic / estimate", r["ratio"], "%", 0)) },
        blocks = listOf(
            Section("Branch data", listOf(
                Field("K", "K-factor", "L/min/bar½"),
                Field("p1", "Pressure at the most remote sprinkler", "bar"),
                Field("C", "Hazen-Williams C", "-", "120 black steel (wet)"),
                Field("n", "Number of sprinklers on the branch (1–15)", "no.", structural = true),
                Field("dDef", "Pipe size for all segments", kind = Kind.SELECT, options = SCH),
                Field("lenDef", "Spacing for all segments", "m")
            ), action = Action("Apply size & spacing to all pipes") { s -> s.list("seg").forEach { it["d"] = s.s("dDef"); it["len"] = s.s("lenDef") } }, diagram = "ff-branch"),
            SketchBlock,
            Grid("Sprinklers (1 = most remote → riser)", "seg",
                help = "Each row = a sprinkler and the pipe after it toward the riser. Elevation drop = how much LOWER the next point is (negative if higher).",
                count = { s -> Eng.branchCount(s) }, rowLabel = { i -> "S${i + 1}" },
                cols = listOf(Field("d", "Pipe (Sch 40 ID)", kind = Kind.SELECT, options = SCH, width = 150), Field("len", "Length", "m"),
                    Field("fit", "Fittings eq. L", "m"), Field("dz", "Elev. drop", "m")),
                outs = listOf(Out("P (bar)", 3) { r, i -> r.row(i, "P") }, Out("q (L/min)", 1) { r, i -> r.row(i, "q") },
                    Out("Q pipe (L/min)", 1) { r, i -> r.row(i, "Q") }, Out("p (bar/m)", 4) { r, i -> r.row(i, "p") },
                    Out("Loss (bar)", 3) { r, i -> r.row(i, "loss") }, Out("P next (bar)", 3, key = true) { r, i -> r.row(i, "next") },
                    Out("v (m/s)", 2, summary = true) { r, i -> r.row(i, "v") }, Out("Velocity check", summary = true) { r, i -> r.row(i, "check") }),
                results = { _, r -> listOf(RRow("Total flow on the branch", r["Q"], "L/min", 1, true), RRow("Total flow", r["Qls"], "L/s", 2), RRow("Total flow", r["Qgpm"], "gpm", 1),
                    RRow("Pressure needed at branch inlet", r["Pin"], "bar", 3, true), RRow("Pressure needed at branch inlet", r["PinM"], "m", 1),
                    RRow("Density × area estimate", r["est"], "L/min", 1), RRow("Hydraulic flow / estimate", r["ratio"], "%", 0), RRow("Highest velocity", r["vMax"], "m/s", 2, true)) }),
            Section("Comparison & velocity limits", listOf(
                Field("density", "Design density (for comparison)", "mm/min", "Light 4.07 · OH1 6.11 · OH2 8.15"),
                Field("coverage", "Coverage per sprinkler", "m²"),
                Field("vGood", "Comfortable velocity (green below)", "m/s"),
                Field("vPractice", "Practice limit for sprinkler pipes", "m/s", "NFPA 13 sets none; common practice"),
                Field("vMax", "Absolute maximum (EN 12845)", "m/s"))),
            Note("q = K√P · Q = cumulative flow · p (bar/m) = 6.05×10⁵ × Q^1.85 / (C^1.85 × d^4.87), Q in L/min, d = INTERNAL Ø in mm · loss = p × (L + Le) · v = Q / A. Pump suction (NFPA 20): ≤ 4.6 m/s at 150 % flow. Project specs may set lower limits – they govern.")
        )
    )
}

object HvacTools {
    private val ORIENT_OPTS = Hvac.ORIENT.map { Opt(it.first, it.first) }
    private val OCC_OPTS = Hvac.OCC.map { Opt(it.name, it.name) }
    private val EXH_OPTS = Hvac.EXH.map { Opt(it.name, it.name) }
    private val YESNO = opts("Yes", "No")

    private fun room(name: String, area: Any?, people: Any?, or: String, wall: Any?, glass: Any?, roof: Any?, light: Any? = 10, equip: Any? = 15) =
        row("name" to name, "area" to area, "people" to people, "orient" to or, "wall" to wall, "glass" to glass, "roof" to roof, "light" to light, "equip" to equip, "oa" to "")
    private fun orientRows() = Hvac.ORIENT.map { row("etd" to it.second, "shgf" to it.third) }
    private fun clBase(rooms: List<RowData>) = state("toDb" to 46, "toWb" to 30, "tiDb" to 24, "tiRh" to 50, "tSupply" to 13,
        "uWall" to 0.57, "uRoof" to 0.3, "uGlass" to 1.9, "sc" to 0.4, "clf" to 0.75, "pplS" to 75, "pplL" to 55, "m2pp" to 10, "rp" to 2.5, "ra" to 0.3, "safety" to 10,
        lists = mapOf("rooms" to rooms, "orient" to orientRows()))

    val cooling = ToolDef(
        id = "hvac-load", title = "Cooling load", sub = "Room by room: walls, glass, roof, people, lights, equipment, fresh air → TR & CFM",
        defaults = { clBase(listOf(
            room("Open office", 120, "", "W", 30, 18, 0), room("Meeting room", 30, 12, "S", 12, 6, 0),
            room("Manager office", 20, 2, "E", 10, 5, 0), room("Reception", 40, 6, "N", 14, 8, 0))) },
        blank = { clBase(listOf(room("Room 1", "", "", "N", "", "", ""))) },
        compute = Hvac::coolingLoad,
        tiles = { _, r -> listOf(RRow("Total cooling", r["tr"], "TR", 2), RRow("Total cooling", r["kw"], "kW", 1), RRow("Supply air", r["cfm"], "CFM", 0),
            RRow("Fresh air", r["oa"], "L/s", 0), RRow("Check", r["m2tr"], "m²/TR", 1)) },
        blocks = listOf(
            Section("1 · Design conditions", listOf(
                Field("toDb", "Outdoor dry bulb", "°C", "UAE design ≈ 46 °C DB / 30 °C WB – use the authority values"),
                Field("toWb", "Outdoor wet bulb", "°C"),
                Field("tiDb", "Indoor dry bulb", "°C"),
                Field("tiRh", "Indoor relative humidity", "%"),
                Field("tSupply", "Supply air temperature (off coil)", "°C", "Used for the supply airflow"),
                Field("safety", "Safety factor", "%")
            ), results = { _, r -> listOf(RRow("Outdoor humidity ratio", r["wo"], "g/kg", 1), RRow("Indoor humidity ratio", r["wi"], "g/kg", 1),
                RRow("ΔT outdoor – indoor", r["dT"], "K", 1), RRow("ΔW outdoor – indoor", r["dW"], "g/kg", 1), RRow("Supply ΔT (room – supply)", r["dTs"], "K", 1)) }, diagram = "hv-cond"),
            Section("2 · Envelope & internal gains", listOf(
                Field("uWall", "Wall U-value", "W/m²K", "UAE green building limits ≈ 0.57 wall / 0.3 roof"),
                Field("uRoof", "Roof U-value", "W/m²K"),
                Field("uGlass", "Glass U-value", "W/m²K"),
                Field("sc", "Glass shading coefficient (SC ≈ SHGC / 0.87)", "-"),
                Field("clf", "Glass cooling load factor (storage)", "-", "≈ 0.6–0.8 for peak"),
                Field("pplS", "People sensible heat", "W/person", "Office work 75 / 55"),
                Field("pplL", "People latent heat", "W/person"),
                Field("m2pp", "Area per person when people is blank", "m²/person"),
                Field("rp", "Fresh air per person (if room OA blank)", "L/s·person", "ASHRAE 62.1 office 2.5"),
                Field("ra", "Fresh air per m² (if room OA blank)", "L/s·m²", "ASHRAE 62.1 office 0.3")
            ), diagram = "hv-env"),
            Grid("3 · Orientation data (peak, ~24° N)", "orient",
                help = "Wall ETD = equivalent temperature difference for walls in that orientation; Horizontal ETD is used for roofs. SHGF = peak solar heat gain through clear glass.",
                fixed = Hvac.ORIENT.map { listOf(it.first) }, fixedCols = listOf(FixedCol("Orientation")),
                cols = listOf(Field("etd", "Wall / roof ETD", "K"), Field("shgf", "Glass SHGF", "W/m²")),
                outs = emptyList()),
            Grid("4 · Rooms", "rooms", titleKey = "name",
                help = "Areas in m². Wall = net opaque external wall, glass = window area, roof = only for top-floor rooms. People / fresh air blank → worked out from the area.",
                add = { room("Room", "", "", "N", "", "", "") },
                cols = listOf(Field("name", "Room", kind = Kind.TEXT, width = 180), Field("area", "Floor area", "m²"), Field("people", "People", "no."),
                    Field("orient", "Facing", kind = Kind.SELECT, options = ORIENT_OPTS, width = 130), Field("wall", "Ext. wall", "m²"), Field("glass", "Glass", "m²"),
                    Field("roof", "Roof", "m²"), Field("light", "Lighting", "W/m²"), Field("equip", "Equipment", "W/m²"), Field("oa", "Fresh air", "L/s")),
                outs = listOf(
                    Out("People used", 0) { r, i -> r.row(i, "people") }, Out("Fresh air used (L/s)", 0) { r, i -> r.row(i, "oa") },
                    Out("Room sensible (W)", 0) { r, i -> r.row(i, "rsh") }, Out("Room latent (W)", 0) { r, i -> r.row(i, "rlh") },
                    Out("Fresh air load (W)", 0) { r, i -> r.row(i, "oaLoad") }, Out("Total (W)", 0) { r, i -> r.row(i, "total") },
                    Out("TR", 2, key = true) { r, i -> r.row(i, "tr") }, Out("Supply air (L/s)", 0) { r, i -> r.row(i, "sa") },
                    Out("Supply air (CFM)", 0, summary = true) { r, i -> r.row(i, "cfm") }, Out("m²/TR", 1, summary = true) { r, i -> r.row(i, "m2tr") }),
                totals = { r -> listOf("Room sensible (W)" to r["rsh"], "Room latent (W)" to r["rlh"], "Fresh air load (W)" to r["oaLoad"], "Total (W)" to r["total"],
                    "TR" to r["tr"], "Supply air (L/s)" to r["sa"], "Supply air (CFM)" to r["cfm"]) },
                results = { _, r -> listOf(RRow("Total floor area", r["area"], "m²", 0), RRow("Total people", r["people"], "no.", 0),
                    RRow("Room sensible heat (RSH)", r["rsh"], "W", 0), RRow("Room latent heat (RLH)", r["rlh"], "W", 0), RRow("Sensible heat ratio", r["shr"], "-", 2),
                    RRow("Fresh air", r["oa"], "L/s", 0), RRow("Fresh air load", r["oaLoad"], "W", 0),
                    RRow("TOTAL COOLING LOAD", r["kw"], "kW", 2, true), RRow("TOTAL COOLING LOAD", r["tr"], "TR", 2, true),
                    RRow("Supply airflow", r["sa"], "L/s", 0), RRow("Supply airflow", r["cfm"], "CFM", 0, true),
                    RRow("Load density", r["wm2"], "W/m²", 0), RRow("Area per TR", r["m2tr"], "m²/TR", 1)) }),
            Note("Peak-load method: walls / roof U·A·ETD, glass U·A·ΔT + A·SHGF·SC·CLF, people, lights & equipment, fresh air 1.23·Q·ΔT + 3010·Q·ΔW, × safety. Supply air = RSH / (1.23 × ΔT supply). For a quick check, UAE offices typically land around 15–25 m²/TR. For VRF / split selection, size each indoor unit from its room total and the outdoor unit from the sum with diversity. Use an hourly method (HAP / Trace / IES) for the final design.")
        )
    )

    private fun space(name: String, cat: String, area: Any?, people: Any? = "") = row("name" to name, "cat" to cat, "area" to area, "people" to people, "ez" to 1)
    private fun exh(name: String, type: String, qty: Any?, area: Any?, h: Any? = 3, ach: Any? = "") = row("name" to name, "type" to type, "qty" to qty, "area" to area, "height" to h, "ach" to ach)
    val freshAir = ToolDef(
        id = "hvac-air", title = "Fresh air & exhaust", sub = "ASHRAE 62.1 ventilation rate procedure + exhaust rates",
        defaults = { state(lists = mapOf(
            "spaces" to listOf(space("Open office", "Office space", 120), space("Meeting room", "Conference / meeting", 30, 12), space("Reception", "Reception area", 40), space("Corridor", "Corridor / lobby (office)", 25)),
            "exhaust" to listOf(exh("Male toilet", "Toilet – public (per WC / urinal)", 4, 15), exh("Female toilet", "Toilet – public (per WC / urinal)", 3, 12),
                exh("Pantry", "Kitchenette", "", 8), exh("Store", "By air changes per hour (ACH)", "", 6, 3, 6)))) },
        blank = { state(lists = mapOf("spaces" to listOf(space("Space 1", "Office space", "")), "exhaust" to listOf(exh("Toilet 1", "Toilet – public (per WC / urinal)", "", "")))) },
        compute = Hvac::freshAir,
        tiles = { _, r -> listOf(RRow("Fresh air", r["oa"], "L/s", 0), RRow("Fresh air", r["oaCfm"], "CFM", 0), RRow("Exhaust", r["exhaust"], "L/s", 0), RRow("Balance", r["balance"], "L/s", 0)) },
        blocks = listOf(
            Grid("1 · Fresh air per space (ASHRAE 62.1 Table 6-1)", "spaces", titleKey = "name",
                help = "Vbz = Rp × people + Ra × area; Voz = Vbz / Ez. People blank → default occupant density of the category. Ez = 1.0 for ceiling supply of cool air.",
                add = { space("Space", "Office space", "") },
                cols = listOf(Field("name", "Space", kind = Kind.TEXT, width = 170), Field("cat", "Category", kind = Kind.SELECT, options = OCC_OPTS, width = 220),
                    Field("area", "Area", "m²"), Field("people", "People", "no."), Field("ez", "Ez")),
                outs = listOf(Out("Rp (L/s·p)", 1) { r, i -> r.row(i, "rp") }, Out("Ra (L/s·m²)", 2) { r, i -> r.row(i, "ra") },
                    Out("People used", 0) { r, i -> r.row(i, "p") }, Out("Voz (L/s)", 1, key = true) { r, i -> r.row(i, "voz") },
                    Out("Voz (CFM)", 0, summary = true) { r, i -> r.row(i, "cfm") }, Out("L/s per m²", 2) { r, i -> r.row(i, "perM2") }),
                totals = { r -> listOf("Voz (L/s)" to r["oa"], "Voz (CFM)" to r["oaCfm"]) }),
            Grid("2 · Exhaust (ASHRAE 62.1 Table 6-4)", "exhaust", titleKey = "name",
                help = "Toilets & showers: quantity = number of WCs / urinals / showerheads. Kitchens, car parks, stores: by area. ACH rows: area × height × ACH / 3.6.",
                add = { exh("Exhaust", "Toilet – public (per WC / urinal)", "", "") },
                cols = listOf(Field("name", "Space", kind = Kind.TEXT, width = 160), Field("type", "Type", kind = Kind.SELECT, options = EXH_OPTS, width = 240),
                    Field("qty", "Fixtures", "no."), Field("area", "Area", "m²"), Field("height", "Height", "m"), Field("ach", "ACH", "1/h")),
                outs = listOf(Out("Rate") { r, i -> r.list("ex").getOrNull(i)?.get("rate") }, Out("Exhaust (L/s)", 1, key = true) { r, i -> r.list("ex").getOrNull(i)?.get("ls") },
                    Out("Exhaust (CFM)", 0, summary = true) { r, i -> r.list("ex").getOrNull(i)?.get("cfm") }, Out("Resulting ACH", 1) { r, i -> r.list("ex").getOrNull(i)?.get("ach") }),
                totals = { r -> listOf("Exhaust (L/s)" to r["exhaust"], "Exhaust (CFM)" to r["exhaustCfm"]) },
                results = { _, r -> listOf(RRow("Total fresh air", r["oa"], "L/s", 0, true), RRow("Total exhaust", r["exhaust"], "L/s", 0, true),
                    RRow("Fresh air – exhaust", r["balance"], "L/s", 0), RRow("Building pressure", r["balanceTxt"])) }),
            Note("Keep the building slightly positive (fresh air > exhaust) in hot humid climates to limit infiltration. Toilets and kitchens stay negative to their surroundings. Authority rules (e.g. ADM / Dubai Municipality, Civil Defence for car-park ventilation) may require higher rates – they govern.")
        )
    )

    private fun duct(name: String, type: String, q: Any?, len: Any?, k: Any?, crit: String = "Yes", shape: String = "Rectangular") =
        row("name" to name, "type" to type, "shape" to shape, "q" to q, "len" to len, "k" to k, "crit" to crit, "depth" to "")
    val ducts = ToolDef(
        id = "hvac-duct", title = "Duct sizing & ESP", sub = "Equal friction, rectangular equivalent, velocity check, fan ESP",
        defaults = { state("rTarget" to 1.0, "depth" to 300, "step" to 50, "eps" to 0.09, "rho" to 1.2, "vMain" to 8, "vBranch" to 6, "vRun" to 4,
            "pTerm" to 25, "pRet" to 50, "pDev" to 30, "safety" to 10, lists = mapOf("seg" to listOf(
                duct("AHU → first tee", "Main", 1600, 12, 1.2), duct("Main after tee", "Main", 1000, 15, 0.6), duct("Branch to open office", "Branch", 400, 10, 0.9),
                duct("Run-out to diffuser", "Run-out", 100, 4, 1.5, shape = "Round"), duct("Branch to meeting room", "Branch", 300, 8, 0.9, crit = "No")))) },
        blank = { state("rTarget" to 1.0, "depth" to 300, "step" to 50, "eps" to 0.09, "rho" to 1.2, "vMain" to 8, "vBranch" to 6, "vRun" to 4,
            "pTerm" to 25, "pRet" to 50, "pDev" to 30, "safety" to 10, lists = mapOf("seg" to listOf(duct("Section 1", "Main", "", "", "")))) },
        compute = Hvac::ducts,
        tiles = { s, r -> listOf(RRow("Sections", s.list("seg").size.toDouble(), "", 0), RRow("Critical path", r["crit"], "Pa", 0),
            RRow("Fan ESP", r["esp"], "Pa", 0), RRow("Fan ESP", r["espIn"], "in. wg", 2), RRow("Checks", if ((r["bad"] as Int) > 0) "${r["bad"]} to fix" else "All OK")) },
        blocks = listOf(
            Section("1 · Design criteria", listOf(
                Field("rTarget", "Friction rate (equal friction)", "Pa/m", "Low pressure supply 0.8–1.2"),
                Field("depth", "Maximum duct depth (ceiling space)", "mm"),
                Field("step", "Round sizes up to", "mm"),
                Field("vMain", "Max velocity – main", "m/s"),
                Field("vBranch", "Max velocity – branch", "m/s"),
                Field("vRun", "Max velocity – run-out", "m/s"),
                Field("eps", "Duct roughness (galvanized 0.09)", "mm"),
                Field("rho", "Air density", "kg/m³")
            ), diagram = "hv-duct"),
            Grid("2 · Duct sections", "seg", titleKey = "name",
                help = "Flow in L/s (1 L/s = 2.12 CFM). ΣK = sum of fitting loss coefficients (elbow ≈ 0.2–0.3, tee branch ≈ 0.5–1). Mark the sections on the longest / worst path as critical. Depth blank = max depth above.",
                add = { duct("Section", "Branch", "", "", "") },
                cols = listOf(Field("name", "Section", kind = Kind.TEXT, width = 190), Field("type", "Type", kind = Kind.SELECT, options = opts("Main", "Branch", "Run-out"), width = 120),
                    Field("shape", "Shape", kind = Kind.SELECT, options = opts("Rectangular", "Round"), width = 140),
                    Field("q", "Airflow", "L/s"), Field("len", "Length", "m"), Field("k", "ΣK fittings"), Field("crit", "Critical path?", kind = Kind.SELECT, options = YESNO),
                    Field("depth", "Depth", "mm")),
                outs = listOf(Out("Round Ø calc (mm)", 0) { r, i -> r.row(i, "dCalc") }, Out("Size (mm)", key = true) { r, i -> r.row(i, "size") },
                    Out("Velocity (m/s)", 2, summary = true) { r, i -> r.row(i, "v") }, Out("Friction (Pa/m)", 2) { r, i -> r.row(i, "r") },
                    Out("Duct loss (Pa)", 1) { r, i -> r.row(i, "fr") }, Out("Fittings loss (Pa)", 1) { r, i -> r.row(i, "fit") },
                    Out("Total (Pa)", 1) { r, i -> r.row(i, "tot") }, Out("Check", summary = true) { r, i -> r.row(i, "check") })),
            Section("3 · Fan external static pressure", listOf(
                Field("pTerm", "Supply diffuser / grille", "Pa"),
                Field("pRet", "Return path (grille, plenum, duct)", "Pa"),
                Field("pDev", "Dampers, fire dampers, sound attenuator", "Pa"),
                Field("safety", "Safety margin", "%")
            ), results = { _, r -> listOf(RRow("Critical path duct + fittings", r["crit"], "Pa", 0), RRow("Terminals & devices", r["extra"], "Pa", 0),
                RRow("FAN ESP", r["esp"], "Pa", 0, true), RRow("FAN ESP", r["espIn"], "in. wg", 2, true)) }, diagram = "hv-esp"),
            Note("Friction: Darcy–Weisbach with Altshul–Tsal f. Rectangular equivalent: De = 1.30 (ab)^0.625 / (a+b)^0.25 (Huebscher). Fitting loss = ΣK × ρv²/2. ESP excludes the coil and filters inside the unit – check what the manufacturer's ESP includes.")
        )
    )
}

object Catalog {
    val BRANCHES = listOf(
        Branch("water", "Water Supply", "WS", 0xFF1F6FD1, "Demand, WSFU & Hunter, pipe sizing, friction, pumps, hot water",
            listOf(PlumbingTools.wsBuilding, PlumbingTools.wsSegments, PlumbingTools.wsHot)),
        Branch("drainage", "Drainage", "DR", 0xFF8A5A1E, "DFU, soil & waste, branches, stacks, building drain, vents",
            listOf(PlumbingTools.drFixtures, PlumbingTools.drPipes)),
        Branch("rain", "Rainwater", "RW", 0xFF0E8A83, "Roof flow, leaders, horizontal storm drains (Manning)",
            listOf(PlumbingTools.rnLeaders, PlumbingTools.rnDrains)),
        Branch("fire", "Fire Fighting", "FF", 0xFFC9342B, "Density/area, UAE risers, pump & tank, branch hydraulics",
            listOf(FireTools.demand, FireTools.zones, FireTools.branch)),
        Branch("hvac", "HVAC", "AC", 0xFF5B4BD6, "Cooling load, fresh air & exhaust, duct sizing & fan ESP",
            listOf(HvacTools.cooling, HvacTools.freshAir, HvacTools.ducts))
    )
    val TOOLS: List<ToolDef> = BRANCHES.flatMap { it.tools }
}
