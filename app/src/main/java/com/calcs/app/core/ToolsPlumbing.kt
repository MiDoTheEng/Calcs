package com.calcs.app.core

/* Water supply, drainage and rainwater tool screens. */
object PlumbingTools {
    private val YESNO = opts("Yes", "No")
    private val YN = optPairs("no" to "No", "yes" to "Yes")
    private val DRAIN_SEL = optPairs("" to "—", 1.25 to "1¼\"", 1.5 to "1½\"", 2 to "2\"", 2.5 to "2½\"", 3 to "3\"", 4 to "4\"", 5 to "5\"", 6 to "6\"", 8 to "8\"")
    private val LEADER_SEL = optPairs("" to "—", 2 to "2\"", 2.5 to "2½\"", 3 to "3\"", 4 to "4\"", 5 to "5\"", 6 to "6\"", 8 to "8\"")

    /* rooms: a room type and its usual fixtures (index in the code table, qty) */
    private val WS_ROOMS = listOf(
        RoomTemplate("Bathroom", "bathgroup", listOf(23 to 1, 11 to 1, 15 to 1)),
        RoomTemplate("Kitchen", "sink", listOf(8 to 1, 19 to 1)),
        RoomTemplate("Guest toilet", "wc", listOf(23 to 1, 11 to 1)),
        RoomTemplate("Laundry", "washer", listOf(19 to 1, 10 to 1)),
        RoomTemplate("Other room", "sink", emptyList()),
        RoomTemplate("Public toilet", "wc", listOf(25 to 1, 12 to 1), common = true),
        RoomTemplate("Gym / changing", "shower", listOf(25 to 1, 12 to 1, 14 to 1), common = true),
        RoomTemplate("Other room", "sink", emptyList(), common = true))
    private val DR_ROOMS = listOf(
        RoomTemplate("Bathroom", "bathgroup", listOf(0 to 1, 8 to 1, 9 to 1)),
        RoomTemplate("Kitchen", "sink", listOf(12 to 1, 13 to 1, 14 to 1)),
        RoomTemplate("Guest toilet", "wc", listOf(0 to 1, 8 to 1)),
        RoomTemplate("Laundry", "washer", listOf(14 to 1, 16 to 1)),
        RoomTemplate("Other room", "sink", emptyList()),
        RoomTemplate("Public toilet", "wc", listOf(2 to 1, 8 to 1, 18 to 1), common = true),
        RoomTemplate("Gym / changing", "shower", listOf(2 to 1, 8 to 1, 9 to 1), common = true),
        RoomTemplate("Other room", "sink", emptyList(), common = true))
    private val HOT_ROOMS = listOf(
        RoomTemplate("Bathroom", "bathgroup", listOf(0 to 1, 3 to 1)),
        RoomTemplate("Kitchen", "sink", listOf(4 to 1, 5 to 1)),
        RoomTemplate("Guest toilet", "wc", listOf(0 to 1)),
        RoomTemplate("Laundry", "washer", listOf(6 to 1)),
        RoomTemplate("Public toilet", "wc", listOf(1 to 1)),
        RoomTemplate("Other room", "sink", emptyList()))
    private fun rm(l: List<RoomTemplate>, name: String, n: Int, common: Boolean = false) = Rooms.room(l.first { it.name == name && it.common == common }, n)

    /* ============================== WATER SUPPLY ============================== */
    private fun wsFixDefaults(): List<RowData> = Eng.WSFU.indices.map { row("unit" to 0, "extra" to 0) }
    val wsBuilding = ToolDef(
        id = "ws-building", title = "Building demand & pump", sub = "Tank, WSFU → peak flow, main pipe, friction, pump",
        defaults = {
            val fix = wsFixDefaults()
            state("roomsOn" to 1, "units" to 20, "persons" to 5, "lpd" to 200, "days" to 1.5, "vTarget" to 1.5, "id" to 66, "vMax" to 2, "C" to 150,
                "len" to 60, "fpct" to 30, "feq" to "", "stat" to 15, "resBar" to 1.5, "safety" to 10, "eff" to 60, lists = mapOf("fix" to fix, "rooms" to listOf(rm(WS_ROOMS, "Bathroom", 2), rm(WS_ROOMS, "Kitchen", 1))))
        },
        blank = {
            state("roomsOn" to 1, "units" to 1, "persons" to 5, "lpd" to 200, "days" to 1.5, "vTarget" to 1.5, "id" to "", "vMax" to 2, "C" to 150,
                "len" to "", "fpct" to 30, "feq" to "", "stat" to "", "resBar" to 1.5, "safety" to 10, "eff" to 60, lists = mapOf("fix" to wsFixDefaults()))
        },
        compute = Eng::wsBuilding,
        tiles = { _, r ->
            listOf(RRow("Storage tank", r["tank"], "m³", 1), RRow("Total WSFU", r["totWsfu"], "WSFU", 1), RRow("Peak flow", r["ls"], "L/s", 2),
                RRow("Pump duty", "${fmt(r["m3h"], 1)} m³/h @ ${fmt(r["head"], 1)} m"), RRow("Shaft power", r["shaft"], "kW", 2))
        },
        blocks = listOf(
            Section("1 · Building data & storage tank", listOf(
                Field("units", "Number of identical units (apartments)", "no."),
                Field("persons", "Persons per unit", "no."),
                Field("lpd", "Daily consumption per person", "L/p/day", "Authority / project requirement"),
                Field("days", "Storage duration", "days")
            ), results = { _, r -> listOf(RRow("Total persons", r["persons"], "no.", 0), RRow("Daily demand", r["daily"], "m³/day", 2), RRow("Required storage volume", r["tank"], "m³", 2, true)) }, diagram = "ws-tank"),
            Rooms("2 · Fixtures, room by room", "rooms", "fix", Eng.WSFU.map { listOf(it.fixture, "${it.occ} · ${it.control}") }, Eng.WSFU.map { fixtureIcon(it.fixture) },
                WS_ROOMS, perUnit = true, help = "Add the rooms of ONE apartment (e.g. 2 bathrooms + 1 kitchen) – they are multiplied by the number of apartments. Rooms outside the apartments (lobby toilets, gym…) are counted once.",
                total = { r -> listOf(RRow("Total WSFU (building)", r["totWsfu"], "WSFU", 1, true), RRow("Peak flow", r["ls"], "L/s", 2)) }),
            Grid("Fixture totals & WSFU (IPC E103.3)", "fix", readOnly = true,
                help = "Filled from the rooms above. Bathroom group = WC + lavatory + bath/shower: use it OR the single fixtures, never both.",
                fixed = Eng.WSFU.map { listOf(it.fixture, it.occ, it.control, it.total) },
                fixedCols = listOf(FixedCol("Fixture"), FixedCol("Occupancy"), FixedCol("Control"), FixedCol("WSFU each", 2)),
                cols = listOf(Field("unit", "Qty / unit"), Field("extra", "Extra qty")),
                outs = listOf(
                    Out("Total qty", 0) { r, i -> r.row(i, "qty") }, Out("Total WSFU", 1, key = true) { r, i -> r.row(i, "wsfu") },
                    Out("Cold", 1) { r, i -> r.row(i, "cold") }, Out("Hot", 1) { r, i -> r.row(i, "hot") }),
                totals = { r -> listOf("Total qty" to r["lQty"], "Total WSFU" to r["lWsfu"], "Cold" to r["lCold"], "Hot" to r["lHot"]) },
                icon = { i -> fixtureIcon(Eng.WSFU[i].fixture) },
                skipEmpty = true),
            Grid("2b · Other fixtures (not in the table)", "other", titleKey = "name", minRows = 0, skipEmpty = true,
                help = "A fixture that isn't listed: type its name and its WSFU from the code or manufacturer. Hot = Yes splits it ¾ cold + ¾ hot (IPC rule). Flush valve = Yes switches the Hunter curve.",
                cols = listOf(Field("name", "Fixture", kind = Kind.TEXT, width = 170), Field("wsfu", "WSFU each"), Field("hot", "Hot water?", kind = Kind.SELECT, options = YN),
                    Field("fv", "Flush valve?", kind = Kind.SELECT, options = YN), Field("unit", "Qty / unit"), Field("extra", "Extra qty")),
                outs = listOf(Out("Total qty", 0) { r, i -> r.list("other").getOrNull(i)?.get("qty") }, Out("Total WSFU", 1, key = true) { r, i -> r.list("other").getOrNull(i)?.get("wsfu") }),
                add = { row("name" to "", "wsfu" to "", "hot" to "yes", "fv" to "no", "unit" to 0, "extra" to 0) },
                results = { _, r -> listOf(RRow("Total WSFU, all fixtures (building main)", r["totWsfu"], "WSFU", 1), RRow("Hunter curve used (auto)", r["curve"]),
                    RRow("Peak flow (Hunter, interpolated)", r["gpm"], "gpm", 2), RRow("Peak flow Q", r["ls"], "L/s", 3, true)) }),
            Section("3 · Main pipe sizing", listOf(
                Field("vTarget", "Target design velocity", "m/s"),
                Field("id", "Selected pipe INTERNAL diameter", "mm", "Real ID from the catalogue (≥ required)"),
                Field("vMax", "Maximum allowable velocity", "m/s"),
                Field("C", "Hazen-Williams C", "-", "150 PPR / PVC · 130–140 copper")
            ), results = { _, r -> listOf(RRow("Required internal diameter", r["reqID"], "mm", 1), RRow("Actual velocity", r["v"], "m/s", 2), RRow("Velocity check", r["vCheck"], key = true)) }, diagram = "ws-pipe"),
            Section("4 · Friction loss on the critical path", listOf(
                Field("len", "Straight length, pump → most remote fixture", "m"),
                Field("fpct", "Fittings allowance", "% of L", "Rule of thumb 20–50 %"),
                Field("feq", "OR fittings equivalent length (catalogue)", "m", "If filled, overrides the %")
            ), results = { _, r -> listOf(RRow("Equivalent length Le", r["Le"], "m", 1), RRow("Friction per m (at peak flow)", r["j"], "m/m", 4), RRow("Total friction loss", r["hf"], "m", 2, true)) }, diagram = "ws-friction"),
            Section("5 · Pump selection", listOf(
                Field("stat", "Static height, pump → highest fixture", "m"),
                Field("resBar", "Residual pressure at remote fixture", "bar"),
                Field("safety", "Safety margin", "%"),
                Field("eff", "Pump efficiency (assumed)", "%")
            ), results = { _, r -> listOf(RRow("Residual pressure as head", r["resM"], "m", 2), RRow("Friction loss (section 4)", r["hf"], "m", 2), RRow("Subtotal head", r["sub"], "m", 2),
                RRow("TOTAL PUMP HEAD", r["head"], "m", 2, true), RRow("Pump flow", r["m3h"], "m³/h", 2, true), RRow("Hydraulic power", r["hyd"], "kW", 2), RRow("Pump shaft power", r["shaft"], "kW", 2, true)) }, diagram = "ws-pump"),
            Note("If any flush-valve WC or urinal is present, the flush-valve Hunter curve is used for the whole building. Low floors: check static pressure; PRVs or zoning may be needed. WSFU and Hunter values per IPC Appendix E – verify against your code edition and authority.")
        )
    )

    private fun wsSeg(name: String, gpm: Any?, id: Any?, len: Any?) = row("name" to name, "gpm" to gpm, "vt" to 1.5, "id" to id, "len" to len, "fpct" to 30)
    val wsSegments = ToolDef(
        id = "ws-segments", title = "Pipe sizing & friction", sub = "Segment by segment along the critical path",
        defaults = {
            state("vMax" to 2, "C" to 150, lists = mapOf("seg" to listOf(
                wsSeg("Main (pump → tee)", 61, 61.4, 10), wsSeg("Riser A", 57, 61.4, 3.5), wsSeg("Riser B", 48, 51.4, 3.5), wsSeg("Riser C", 38, 51.4, 3.5),
                wsSeg("Riser D", 26.3, 40.8, 3.5), wsSeg("Floor 4 branch", 26.3, 40.8, 12), wsSeg("Apartment branch", 16.5, 32.6, 9))))
        },
        blank = { state("vMax" to 2, "C" to 150, lists = mapOf("seg" to listOf(wsSeg("Segment 1", "", "", "")))) },
        compute = Eng::wsSegments,
        tiles = { s, r -> listOf(RRow("Segments", s.list("seg").size.toDouble(), "", 0), RRow("Total friction", r["total"], "m", 2),
            RRow("Checks", if ((r["bad"] as Int) > 0) "${r["bad"]} to fix" else "All OK")) },
        blocks = listOf(
            Section("Design limits", listOf(Field("vMax", "Maximum velocity", "m/s"), Field("C", "Hazen-Williams C", "-"))),
            Grid("Segments (pump → most remote fixture)", "seg", titleKey = "name",
                help = "Enter the segments of ONE path to get its total loss. Peak flow from the WSFU each segment serves. ID = real internal diameter from the catalogue.",
                add = { wsSeg("Segment", "", "", "") },
                cols = listOf(Field("name", "Segment", kind = Kind.TEXT, width = 200), Field("gpm", "Peak flow", "gpm"), Field("vt", "Target v", "m/s"),
                    Field("id", "Selected ID", "mm"), Field("len", "Length", "m"), Field("fpct", "Fittings", "%")),
                outs = listOf(
                    Out("Flow (L/s)", 2) { r, i -> r.row(i, "ls") }, Out("Required ID (mm)", 1) { r, i -> r.row(i, "req") },
                    Out("Velocity (m/s)", 2) { r, i -> r.row(i, "v") }, Out("Check", summary = true) { r, i -> r.row(i, "check") },
                    Out("Le (m)", 1) { r, i -> r.row(i, "Le") }, Out("Loss (m)", 3, key = true) { r, i -> r.row(i, "loss") }),
                results = { _, r -> listOf(RRow("TOTAL friction along the path", r["total"], "m", 2, true)) }),
            Note("1 gpm = 0.0630902 L/s. Required ID = √(4Q/πV). hf = 10.67 × Le × Q^1.852 / (C^1.852 × D^4.87), SI units. C = 150 plastic, 130–140 copper, ~100 old steel.")
        )
    )

    val wsHot = ToolDef(
        id = "ws-hot", title = "Hot water heater", sub = "Storage volume & heater power (fixture method)",
        defaults = { state("df" to 0.3, "sf" to 1.25, "tc" to 25, "ts" to 60, "vol" to "", "rec" to 2, "persons" to 4, "lppd" to 50,
            lists = mapOf("qty" to Eng.HOT_FIXTURES.map { row("q" to 0) }, "rooms" to listOf(rm(HOT_ROOMS, "Bathroom", 2), rm(HOT_ROOMS, "Kitchen", 1)))).also { it.f["roomsOn"] = "1" } },
        blank = { state("df" to 0.3, "sf" to 1.25, "tc" to 25, "ts" to 60, "vol" to "", "rec" to 2, "persons" to 4, "lppd" to 50,
            lists = mapOf("qty" to Eng.HOT_FIXTURES.map { row("q" to 0) })).also { it.f["roomsOn"] = "1" } },
        compute = Eng::wsHot,
        tiles = { _, r -> listOf(RRow("Required storage", r["storage"], "L", 0), RRow("Recovery (PMD)", r["pmd"], "L/h", 0), RRow("Heater power", r["pTank"], "kW", 2)) },
        blocks = listOf(
            Rooms("1 · Rooms served by this heater", "rooms", "qty", Eng.HOT_FIXTURES.map { listOf(it.first, "") }, Eng.HOT_FIXTURES.map { fixtureIcon(it.first) },
                HOT_ROOMS, perUnit = false, help = "One heater → only the rooms it serves (e.g. one apartment: 2 bathrooms + 1 kitchen).",
                total = { r -> listOf(RRow("Possible maximum demand", r["possible"], "L/h", 0, true)) }),
            Grid("Fixture totals (ASHRAE / ASPE, apartment house, 60 °C)", "qty", readOnly = true,
                help = "Filled from the rooms above.",
                fixed = Eng.HOT_FIXTURES.map { listOf(it.first, it.second) },
                fixedCols = listOf(FixedCol("Fixture"), FixedCol("Demand (US gal/h)", 0)),
                cols = listOf(Field("q", "Quantity")),
                outs = listOf(Out("L/h each", 1) { r, i -> r.row(i, "lh") }, Out("Total (L/h)", 1, key = true) { r, i -> r.row(i, "total") }),
                totals = { r -> listOf("Total (L/h)" to r["lPossible"]) },
                icon = { i -> fixtureIcon(Eng.HOT_FIXTURES[i].first) }, skipEmpty = true),
            Grid("1b · Other fixtures (not in the table)", "other", titleKey = "name", minRows = 0, skipEmpty = true,
                help = "A fixture that isn't listed: its hot water demand per fixture from the manufacturer (L/h; 1 US gal/h = 3.785 L/h).",
                cols = listOf(Field("name", "Fixture", kind = Kind.TEXT, width = 170), Field("lh", "Demand each", "L/h"), Field("q", "Quantity")),
                outs = listOf(Out("Total (L/h)", 1, key = true) { r, i -> r.list("other").getOrNull(i)?.get("total") }),
                add = { row("name" to "", "lh" to "", "q" to 1) }),
            Section("2 · Demand & storage", listOf(
                Field("df", "Demand factor", "-", "Apartment house 0.30"),
                Field("sf", "Storage capacity factor", "-", "Apartment house 1.25")
            ), results = { _, r -> listOf(RRow("Possible maximum demand", r["possible"], "L/h", 1), RRow("Probable maximum demand (PMD) = recovery", r["pmd"], "L/h", 1), RRow("Required storage volume", r["storage"], "L", 1, true)) }),
            Section("3 · Heater power", listOf(
                Field("tc", "Cold water inlet temperature", "°C"),
                Field("ts", "Storage temperature", "°C", "≥ 60 °C against Legionella"),
                Field("vol", "Selected heater volume (blank = required)", "L"),
                Field("rec", "Recovery time (heat a full tank)", "h")
            ), results = { _, r -> listOf(RRow("ΔT", r["dT"], "°C", 1), RRow("Volume used", r["vol"], "L", 1), RRow("Power to heat the tank in the recovery time", r["pTank"], "kW", 2, true), RRow("Power to keep up with PMD continuously", r["pCont"], "kW", 2)) }, diagram = "ws-heater"),
            Section("4 · Quick check: daily use", listOf(
                Field("persons", "Persons served", "no."), Field("lppd", "Hot water use per person (60 °C)", "L/p/day")
            ), results = { _, r -> listOf(RRow("Daily hot water use", r["daily"], "L/day", 0), RRow("Storage as % of daily use", r["pct"], "%", 0)) }),
            Note("P = V × 4.186 × ΔT / (3600 × t). Store at ≥ 60 °C and deliver through a thermostatic mixing valve where required.")
        )
    )

    /* ================================ DRAINAGE ================================ */
    private fun dfuDefaults(): List<RowData> = Eng.DFU.indices.map { row("unit" to 0, "extra" to 0) }
    val drFixtures = ToolDef(
        id = "dr-fixtures", title = "Fixtures & DFU", sub = "Soil / waste DFU for the building and per unit",
        defaults = {
            state("roomsOn" to 1, "units" to 12, lists = mapOf("fix" to dfuDefaults(), "rooms" to listOf(rm(DR_ROOMS, "Bathroom", 2), rm(DR_ROOMS, "Kitchen", 1), rm(DR_ROOMS, "Gym / changing", 2, true))))
        },
        blank = { state("roomsOn" to 1, "units" to 1, lists = mapOf("fix" to dfuDefaults())) },
        compute = Eng::drFixtures,
        tiles = { _, r -> listOf(RRow("Total DFU", r["total"], "DFU", 1), RRow("Soil DFU", r["soil"], "DFU", 1), RRow("Waste DFU", r["waste"], "DFU", 1),
            RRow("Per unit soil / waste", "${fmt(r["unitSoil"], 0)} / ${fmt(r["unitWaste"], 0)}", "DFU")) },
        blocks = listOf(
            Section("Building", listOf(Field("units", "Number of identical units (apartments)", "no.")), diagram = "dr-building"),
            Rooms("Fixtures, room by room", "rooms", "fix", Eng.DFU.map { listOf(it.fixture, it.occ) }, Eng.DFU.map { fixtureIcon(it.fixture) },
                DR_ROOMS, perUnit = true, help = "Add the rooms of ONE apartment – they are multiplied by the number of apartments. Rooms outside the apartments are counted once.",
                total = { r -> listOf(RRow("Total DFU (building)", r["total"], "DFU", 1, true), RRow("Soil / waste", "${fmt(r["soil"], 0)} / ${fmt(r["waste"], 0)}", "DFU")) }),
            Grid("Fixture totals & DFU (IPC 709.1)", "fix", skipEmpty = true, readOnly = true,
                help = "Filled from the rooms above. Two-pipe system: WC & urinals → soil, the rest → waste. Bathroom groups only for a combined (one-pipe) system.",
                fixed = Eng.DFU.map { listOf(it.fixture, it.occ, it.dfu, it.system) },
                fixedCols = listOf(FixedCol("Fixture"), FixedCol("Occupancy"), FixedCol("DFU each", 1), FixedCol("System")),
                cols = listOf(Field("unit", "Qty / unit"), Field("extra", "Extra qty")),
                outs = listOf(Out("Total qty", 0) { r, i -> r.row(i, "qty") }, Out("Total DFU", 1, key = true) { r, i -> r.row(i, "dfu") }),
                totals = { r -> listOf("Total DFU" to r["lTotal"]) },
                icon = { i -> fixtureIcon(Eng.DFU[i].fixture) }),
            Grid("Other fixtures (not in the table)", "other", titleKey = "name", minRows = 0, skipEmpty = true,
                help = "A fixture that isn't listed: its DFU from the code or manufacturer (or by trap size, IPC 709.2). Soil = WC / urinal type, Waste = the rest.",
                cols = listOf(Field("name", "Fixture", kind = Kind.TEXT, width = 170), Field("dfu", "DFU each"), Field("sys", "System", kind = Kind.SELECT, options = opts("Waste", "Soil")),
                    Field("unit", "Qty / unit"), Field("extra", "Extra qty")),
                outs = listOf(Out("Total qty", 0) { r, i -> r.list("other").getOrNull(i)?.get("qty") }, Out("Total DFU", 1, key = true) { r, i -> r.list("other").getOrNull(i)?.get("dfu") }),
                add = { row("name" to "", "dfu" to "", "sys" to "Waste", "unit" to 0, "extra" to 0) },
                results = { _, r -> buildList {
                    add(RRow("Total DFU (combined / building drain)", r["total"], "DFU", 1, true)); add(RRow("Soil DFU (building)", r["soil"], "DFU", 1))
                    add(RRow("Waste DFU (building)", r["waste"], "DFU", 1)); add(RRow("Soil DFU per unit", r["unitSoil"], "DFU", 1)); add(RRow("Waste DFU per unit", r["unitWaste"], "DFU", 1))
                    if (r["warning"] != null) add(RRow("Warning", r["warning"]))
                } })
        )
    )

    private fun drSeg(name: String, type: String, dfu: Any?, intervals: Any? = "", at: Any? = "", slope: Any? = "", wc: String = "No") =
        row("name" to name, "type" to type, "dfu" to dfu, "intervals" to intervals, "atInterval" to at, "slope" to slope, "wc" to wc, "selected" to "")
    val drPipes = ToolDef(
        id = "dr-pipes", title = "Pipe sizing & vents", sub = "Branches, stacks, building drain (IPC 710.1)",
        defaults = { state(lists = mapOf("seg" to listOf(
            drSeg("Apartment soil branch (WC)", "Horizontal branch", 6, wc = "Yes"), drSeg("Apartment waste branch", "Horizontal branch", 12),
            drSeg("Soil stack (4 floors)", "Stack", 24, 4, 6, wc = "Yes"), drSeg("Waste stack (4 floors)", "Stack", 48, 4, 12),
            drSeg("Building drain @ 2%", "Building drain", 230, slope = 2, wc = "Yes"), drSeg("Building drain @ 4%", "Building drain", 230, slope = 4, wc = "Yes")))) },
        blank = { state(lists = mapOf("seg" to listOf(drSeg("Segment 1", "Horizontal branch", "")))) },
        compute = Eng::drPipes,
        tiles = { s, r -> listOf(RRow("Segments", s.list("seg").size.toDouble(), "", 0), RRow("Checks", if ((r["bad"] as Int) > 0) "${r["bad"]} to fix" else "All OK")) },
        blocks = listOf(
            Grid("Segments", "seg", titleKey = "name",
                help = "Stacks: branch intervals ≈ floors, and the max DFU entering at one interval (needed above 3 intervals). Building drains: slope 1, 2 or 4 %. Take DFU from the Fixtures & DFU tab.",
                add = { drSeg("Segment", "Horizontal branch", "") },
                cols = listOf(Field("name", "Segment", kind = Kind.TEXT, width = 210),
                    Field("type", "Type", kind = Kind.SELECT, options = opts("Horizontal branch", "Stack", "Building drain"), width = 170),
                    Field("dfu", "Total DFU"), Field("intervals", "Branch intervals"), Field("atInterval", "Max DFU at one interval"),
                    Field("slope", "Slope", "%", kind = Kind.SELECT, options = optPairs("" to "—", 1 to "1 %", 2 to "2 %", 4 to "4 %")),
                    Field("wc", "Serves a WC?", kind = Kind.SELECT, options = YESNO), Field("selected", "Your size", kind = Kind.SELECT, options = DRAIN_SEL)),
                outs = listOf(
                    Out("By table") { r, i -> inch(r.row(i, "table")) }, Out("Required", key = true) { r, i -> inch(r.row(i, "required")) },
                    Out("≈ DN (mm)", 0) { r, i -> r.row(i, "dn") }, Out("Check", summary = true) { r, i -> r.row(i, "check") },
                    Out("Min vent", summary = true) { r, i -> inch(r.row(i, "vent")) })),
            Note("WC drains never below 3\". Pipes never reduce in the direction of flow. Stacks over 3 branch intervals: both the total AND the DFU at one interval must fit. Vent ≥ ½ of the drain it serves, min 1¼\" (IPC 906.2). Many regional specs set a 4\" minimum soil stack.")
        )
    )

    /* ================================ RAINWATER ================================ */
    val rnLeaders = ToolDef(
        id = "rn-leaders", title = "Roof areas & leaders", sub = "Q = C·i·A/3600 → vertical leaders (IPC 1106.2)",
        defaults = { state("intensity" to 75, "minLeader" to 3, lists = mapOf("areas" to listOf(row("name" to "Roof", "area" to 300, "c" to 1, "n" to 2, "selected" to "")))) },
        blank = { state("intensity" to 75, "minLeader" to 3, lists = mapOf("areas" to listOf(row("name" to "Area 1", "area" to "", "c" to 1, "n" to 1, "selected" to "")))) },
        compute = Eng::rnLeaders,
        tiles = { _, r -> listOf(RRow("Total area", r["area"], "m²", 0), RRow("Total flow", r["ls"], "L/s", 2), RRow("Total flow", r["gpm"], "gpm", 1)) },
        blocks = listOf(
            Section("Design data", listOf(
                Field("intensity", "Rainfall intensity i", "mm/h", "From the authority for the project location"),
                Field("minLeader", "Minimum leader size used in practice", "in", "0 = follow the table only")), diagram = "rn-design"),
            Grid("Catchment areas & leaders", "areas", titleKey = "name",
                help = "C: roofs / concrete 0.9–1.0, paving 0.7–0.9, gravel 0.4–0.6, landscape 0.1–0.3.",
                add = { row("name" to "Area", "area" to "", "c" to 1, "n" to 1, "selected" to "") },
                cols = listOf(Field("name", "Area", kind = Kind.TEXT, width = 180), Field("area", "Area", "m²"), Field("c", "C"), Field("n", "No. of leaders"),
                    Field("selected", "Your size", kind = Kind.SELECT, options = LEADER_SEL)),
                outs = listOf(Out("Q (L/s)", 2) { r, i -> r.row(i, "ls") }, Out("Q (gpm)", 1) { r, i -> r.row(i, "gpm") },
                    Out("Q per leader (gpm)", 1) { r, i -> r.row(i, "per") }, Out("By table") { r, i -> inch(r.row(i, "table")) },
                    Out("Required", key = true) { r, i -> inch(r.row(i, "required")) }, Out("≈ DN (mm)", 0) { r, i -> r.row(i, "dn") },
                    Out("Check", summary = true) { r, i -> r.row(i, "check") }),
                totals = { r -> listOf("Area" to r["area"], "Q (L/s)" to r["ls"], "Q (gpm)" to r["gpm"]) }),
            Note("Q = C × i × A / 3600 → L/s (1 mm of rain on 1 m² = 1 litre). Use more than one drain per roof and provide overflow drains / scuppers where parapets can trap water.")
        )
    )

    val rnDrains = ToolDef(
        id = "rn-drains", title = "Horizontal storm drains", sub = "Manning, pipe flowing full",
        defaults = { state(lists = mapOf("seg" to listOf(row("name" to "Roof leaders → storm manhole", "q" to 6.25, "slope" to 1, "n" to 0.009)))) },
        blank = { state(lists = mapOf("seg" to listOf(row("name" to "Segment 1", "q" to "", "slope" to 1, "n" to 0.009)))) },
        compute = Eng::rnDrains,
        tiles = { s, r -> listOf(RRow("Segments", s.list("seg").size.toDouble(), "", 0), RRow("Checks", if ((r["bad"] as Int) > 0) "${r["bad"]} to fix" else "All OK")) },
        blocks = listOf(
            Grid("Segments", "seg", titleKey = "name",
                help = "Flow from the Roof areas tab (L/s). n ≈ 0.009–0.011 smooth plastic, 0.013 concrete.",
                add = { row("name" to "Segment", "q" to "", "slope" to 1, "n" to 0.009) },
                cols = listOf(Field("name", "Segment", kind = Kind.TEXT, width = 220), Field("q", "Flow Q", "L/s"), Field("slope", "Slope", "%"), Field("n", "Manning n")),
                outs = listOf(Out("Required Ø (mm)", 1) { r, i -> r.row(i, "req") }, Out("Selected (mm)", 0, key = true) { r, i -> r.row(i, "nominal") },
                    Out("Capacity full (L/s)", 2) { r, i -> r.row(i, "cap") }, Out("Velocity full (m/s)", 2) { r, i -> r.row(i, "v") },
                    Out("Check", summary = true) { r, i -> r.row(i, "check") })),
            Note("D = [ Q × n × 4^(5/3) / (π × √S) ]^(3/8), Q in m³/s, S as decimal. Selected = first of 75, 100, 150, 200, 250, 300, 375, 450 mm (used as internal Ø – replace with catalogue IDs).")
        )
    )
}
