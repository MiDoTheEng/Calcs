/* Calcs – calculation engine.
   Pure functions + code tables. Mirrors the Excel calculators
   (Water supply, Drainage, Rainwater, Fire fighting). */
(function (root) {
  'use strict';

  var GPM_LS = 0.0630902;      // 1 gpm = L/s
  var GPM_LMIN = 3.78541;      // 1 gpm = L/min
  var G = 9.80665;
  var BAR_M = 100 / G;         // 1 bar = 10.197 m

  function num(v) {
    if (v === null || v === undefined || v === '') return NaN;
    var n = typeof v === 'number' ? v : parseFloat(String(v).replace(',', '.'));
    return isFinite(n) ? n : NaN;
  }
  function has(v) { return !isNaN(num(v)); }
  function n0(v) { var n = num(v); return isNaN(n) ? 0 : n; }
  function hw(Qm3s, C, Dm) { // Hazen-Williams, SI: m/m
    return 10.67 * Math.pow(Qm3s, 1.852) / (Math.pow(C, 1.852) * Math.pow(Dm, 4.87));
  }
  function areaM2(dmm) { return Math.PI * Math.pow(dmm / 1000, 2) / 4; }
  // first index where list[i] >= x  (Excel: MATCH(x-0.0001, list, 1)+1)
  function firstAtLeast(list, x) {
    for (var i = 0; i < list.length; i++) if (list[i] !== null && list[i] >= x - 1e-9) return i;
    return -1;
  }

  /* ------------------------------------------------------------------ */
  /* TABLES                                                              */
  /* ------------------------------------------------------------------ */
  var WSFU = [ // [fixture, occupancy, control, cold, hot, total]  IPC E103.3(2)
    ['Bathroom group', 'Private', 'Flush tank', 2.7, 1.5, 3.6],
    ['Bathroom group', 'Private', 'Flush valve', 6, 3, 8],
    ['Bathtub', 'Private', 'Faucet', 1, 1, 1.4],
    ['Bathtub', 'Public', 'Faucet', 3, 3, 4],
    ['Bidet', 'Private', 'Faucet', 1.5, 1.5, 2],
    ['Combination fixture', 'Private', 'Faucet', 2.25, 2.25, 3],
    ['Dishwashing machine', 'Private', 'Automatic', 0, 1.4, 1.4],
    ['Drinking fountain', 'Offices, etc.', '3/8" valve', 0.25, 0, 0.25],
    ['Kitchen sink', 'Private', 'Faucet', 1, 1, 1.4],
    ['Kitchen sink', 'Hotel, restaurant', 'Faucet', 3, 3, 4],
    ['Laundry trays (1 to 3)', 'Private', 'Faucet', 1, 1, 1.4],
    ['Lavatory', 'Private', 'Faucet', 0.5, 0.5, 0.7],
    ['Lavatory', 'Public', 'Faucet', 1.5, 1.5, 2],
    ['Service sink', 'Offices, etc.', 'Faucet', 2.25, 2.25, 3],
    ['Shower head', 'Public', 'Mixing valve', 3, 3, 4],
    ['Shower head', 'Private', 'Mixing valve', 1, 1, 1.4],
    ['Urinal', 'Public', '1" flush valve', 10, 0, 10],
    ['Urinal', 'Public', '3/4" flush valve', 5, 0, 5],
    ['Urinal', 'Public', 'Flush tank', 3, 0, 3],
    ['Washing machine (8 lb)', 'Private', 'Automatic', 1, 1, 1.4],
    ['Washing machine (8 lb)', 'Public', 'Automatic', 2.25, 2.25, 3],
    ['Washing machine (15 lb)', 'Public', 'Automatic', 3, 3, 4],
    ['Water closet', 'Private', 'Flush valve', 6, 0, 6],
    ['Water closet', 'Private', 'Flush tank', 2.2, 0, 2.2],
    ['Water closet', 'Public', 'Flush valve', 10, 0, 10],
    ['Water closet', 'Public', 'Flush tank', 5, 0, 5],
    ['Water closet', 'Public or private', 'Flushometer tank', 2, 0, 2]
  ];
  // Hunter curve IPC E103.3(3): [WSFU, flush tank gpm, flush valve gpm]
  var HUNTER = [[1,3,null],[2,5,null],[3,6.5,null],[4,8,null],[5,9.4,15],[6,10.7,17.4],[7,11.8,19.8],[8,12.8,22.2],[9,13.7,24.6],[10,14.6,27],[11,15.4,27.8],[12,16,28.6],[13,16.5,29.4],[14,17,30.2],[15,17.5,31],[16,18,31.8],[17,18.4,32.6],[18,18.8,33.4],[19,19.2,34.2],[20,19.6,35],[25,21.5,38],[30,23.3,42],[35,24.9,44],[40,26.3,46],[45,27.7,48],[50,29.1,50],[60,32,54],[70,35,58],[80,38,61.2],[90,41,64.3],[100,43.5,67.5],[120,48,73.1],[140,52.5,77.6],[160,57,81.6],[180,61,85.6],[200,65,89.6],[225,70,94.6],[250,75,99.6],[275,80,104.6],[300,85,108.6],[400,105,127.6],[500,124,144.3],[750,170,180.3],[1000,208,208],[1250,239,239],[1500,269,269],[1750,297,297],[2000,325,325],[2500,380,380],[3000,433,433],[4000,525,525],[5000,593,593]];

  function hunter(wsfu, flushValve) {
    if (!(wsfu > 0)) return 0;
    if (wsfu > 5000) return NaN; // above table
    var col = flushValve ? 2 : 1;
    var x = flushValve ? Math.max(wsfu, 5) : wsfu;
    if (x < 1) return x * HUNTER[0][1];
    var i = 0;
    for (var k = 0; k < HUNTER.length; k++) if (HUNTER[k][0] <= x) i = k;
    var a = HUNTER[i], b = HUNTER[i + 1];
    if (!b || a[col] === null || b[col] === null) return a[col];
    return a[col] + (x - a[0]) * (b[col] - a[col]) / (b[0] - a[0]);
  }

  var HOT_FIXTURES = [ // ASHRAE/ASPE, apartment house, US gal/h
    ['Lavatory - private', 2], ['Lavatory - public', 4], ['Bathtub', 20], ['Shower', 30],
    ['Kitchen sink', 10], ['Dishwasher', 15], ['Laundry tub / washing machine', 20],
    ['Pantry sink', 5], ['Service sink', 20]
  ];

  var DFU = [ // [fixture, occupancy, dfu, system]  IPC 709.1
    ['Water closet, 1.6 gpf or less', 'Private', 3, 'Soil'],
    ['Water closet, more than 1.6 gpf', 'Private', 4, 'Soil'],
    ['Water closet, 1.6 gpf or less', 'Public', 4, 'Soil'],
    ['Water closet, more than 1.6 gpf', 'Public', 6, 'Soil'],
    ['Urinal', '-', 4, 'Soil'],
    ['Urinal, 1 gallon or less', '-', 2, 'Soil'],
    ['Bathroom group, WC 1.6 gpf or less', 'Private', 5, 'Combined only'],
    ['Bathroom group, WC more than 1.6 gpf', 'Private', 6, 'Combined only'],
    ['Lavatory', '-', 1, 'Waste'],
    ['Shower', '-', 2, 'Waste'],
    ['Bathtub', '-', 2, 'Waste'],
    ['Bidet', '-', 1, 'Waste'],
    ['Kitchen sink, domestic', 'Private', 2, 'Waste'],
    ['Dishwasher, domestic', 'Private', 2, 'Waste'],
    ['Clothes washer, residential', 'Private', 2, 'Waste'],
    ['Clothes washer, commercial', 'Public', 3, 'Waste'],
    ['Laundry tray', '-', 2, 'Waste'],
    ['Service sink', '-', 2, 'Waste'],
    ['Floor drain', '-', 2, 'Waste'],
    ['Drinking fountain', '-', 0.5, 'Waste']
  ];
  // IPC 710.1(2): size, horizontal branch, stack<=3, stack>3 total, stack>3 at one interval, DN
  var DRAIN_SIZES = [1.5, 2, 2.5, 3, 4, 5, 6, 8];
  var DRAIN_HB = [3, 6, 12, 20, 160, 360, 620, 1400];
  var DRAIN_ST3 = [4, 10, 20, 48, 240, 540, 960, 2200];
  var DRAIN_STT = [8, 24, 42, 72, 500, 1100, 1900, 3600];
  var DRAIN_STI = [2, 6, 9, 20, 90, 200, 350, 600];
  var DRAIN_DN = [40, 50, 65, 80, 100, 125, 150, 200];
  // IPC 710.1(1) building drain: size, 1%, 2%, 4%
  var BD_SIZES = [3, 4, 5, 6, 8];
  var BD = { 1: [36, 180, 390, 700, 1600], 2: [42, 216, 480, 840, 1920], 4: [50, 250, 575, 1000, 2300] };
  var VENTS = [1.25, 1.5, 2, 2.5, 3, 4, 5, 6, 8];

  function dnOf(inch) {
    var i = DRAIN_SIZES.indexOf(inch);
    if (i >= 0) return DRAIN_DN[i];
    return ({ 1.25: 32, 10: 250, 12: 300 })[inch] || '';
  }

  // Rain: IPC 1106.2 leaders
  var LEADERS = [[2, 30, 50], [2.5, 54, 65], [3, 92, 80], [4, 192, 100], [5, 360, 125], [6, 563, 150], [8, 1208, 200]];
  var STORM_PIPES = [75, 100, 150, 200, 250, 300, 375, 450]; // nominal = used internal

  // Fire: NFPA 13 density/area
  var HAZARDS = [ // name, examples, density gpm/ft2, area ft2, hose gpm, dur min, dur max, max coverage ft2
    ['Light', 'Residential, offices, hospitals, schools', 0.10, 1500, 100, 30, 30, 225],
    ['Ordinary Group 1', 'Car parks, restaurant service areas, laundries', 0.15, 1500, 250, 60, 90, 130],
    ['Ordinary Group 2', 'Shops, light storage, workshops', 0.20, 1500, 250, 60, 90, 130],
    ['Extra Group 1', 'Hazardous processes (e.g. some industrial)', 0.30, 2500, 500, 90, 120, 100],
    ['Extra Group 2', 'Flammable liquids processes', 0.40, 2500, 500, 90, 120, 100]
  ];
  var DENS_SI = 40.746, FT2_M2 = 0.092903;
  function hazard(name) {
    for (var i = 0; i < HAZARDS.length; i++) if (HAZARDS[i][0] === name) return HAZARDS[i];
    return null;
  }
  var SCH40 = [['1"', 26.6], ['1¼"', 35.1], ['1½"', 40.9], ['2"', 52.5], ['2½"', 62.7], ['3"', 77.9], ['4"', 102.3], ['6"', 154.1]];

  /* ------------------------------------------------------------------ */
  /* WATER SUPPLY                                                        */
  /* ------------------------------------------------------------------ */
  function wsBuilding(s) {
    var units = n0(s.units);
    var persons = units * n0(s.persons);
    var daily = persons * n0(s.lpd) / 1000;
    var tank = daily * n0(s.days);
    var rows = [], tq = 0, tw = 0, tc = 0, th = 0, fv = false;
    WSFU.forEach(function (f, i) {
      var r = (s.fix && s.fix[i]) || {};
      var q = n0(r.unit) * units + n0(r.extra);
      var w = f[5] * q;
      if (q > 0 && /flush valve/i.test(f[2])) fv = true;
      rows.push({ qty: q, wsfu: w, cold: f[3] * q, hot: f[4] * q });
      tq += q; tw += w; tc += f[3] * q; th += f[4] * q;
    });
    var gpm = hunter(tw, fv);
    var ls = gpm * GPM_LS;
    var reqID = Math.sqrt(4 * (ls / 1000) / (Math.PI * n0(s.vTarget))) * 1000;
    var id = num(s.id);
    var v = (ls / 1000) / areaM2(id);
    var Le = has(s.feq) && num(s.feq) > 0 ? n0(s.len) + num(s.feq) : n0(s.len) * (1 + n0(s.fpct) / 100);
    var j = hw(ls / 1000, n0(s.C), id / 1000);
    var hf = j * Le;
    var resM = n0(s.resBar) * BAR_M;
    var sub = n0(s.stat) + resM + hf;
    var head = sub * (1 + n0(s.safety) / 100);
    var hyd = 1000 * G * (ls / 1000) * head / 1000;
    var shaft = hyd / (n0(s.eff) / 100);
    return {
      persons: persons, daily: daily, tank: tank, rows: rows,
      totQty: tq, totWsfu: tw, totCold: tc, totHot: th,
      curve: fv ? 'Flush valve' : 'Flush tank', gpm: gpm, ls: ls,
      reqID: reqID, v: v, vOK: v <= n0(s.vMax), Le: Le, j: j, hf: hf,
      resM: resM, sub: sub, head: head, m3h: ls * 3.6, hyd: hyd, shaft: shaft
    };
  }

  function wsSegments(s) {
    var tot = 0;
    var rows = (s.seg || []).map(function (r) {
      var gpm = num(r.gpm), o = {};
      if (!(gpm > 0)) return o;
      o.ls = gpm * GPM_LS;
      if (num(r.vt) > 0) o.req = Math.sqrt(4 * (o.ls / 1000) / (Math.PI * num(r.vt))) * 1000;
      var id = num(r.id);
      if (id > 0) {
        o.v = (o.ls / 1000) / areaM2(id);
        o.check = (o.req !== undefined && id < o.req) ? 'ID TOO SMALL' : (o.v > n0(s.vMax) ? 'TOO FAST' : 'OK');
        o.j = hw(o.ls / 1000, n0(s.C), id / 1000);
      }
      if (num(r.len) > 0) o.Le = num(r.len) * (1 + n0(r.fpct) / 100);
      if (o.j !== undefined && o.Le !== undefined) { o.loss = o.j * o.Le; tot += o.loss; }
      return o;
    });
    return { rows: rows, total: tot };
  }

  function wsHot(s) {
    var rows = [], possible = 0;
    HOT_FIXTURES.forEach(function (f, i) {
      var q = n0(s.qty && s.qty[i]);
      var lh = f[1] * GPM_LMIN;
      rows.push({ lh: lh, total: lh * q });
      possible += lh * q;
    });
    var pmd = possible * n0(s.df);
    var storage = pmd * n0(s.sf);
    var dT = n0(s.ts) - n0(s.tc);
    var vol = has(s.vol) && num(s.vol) > 0 ? num(s.vol) : storage;
    var pTank = vol * 4.186 * dT / (3600 * n0(s.rec));
    var pCont = pmd * 4.186 * dT / 3600;
    var daily = n0(s.persons) * n0(s.lppd);
    return { rows: rows, possible: possible, pmd: pmd, storage: storage, dT: dT, vol: vol,
      pTank: pTank, pCont: pCont, daily: daily, pct: daily > 0 ? storage / daily : 0 };
  }

  /* ------------------------------------------------------------------ */
  /* DRAINAGE                                                            */
  /* ------------------------------------------------------------------ */
  function drFixtures(s) {
    var units = n0(s.units), rows = [], T = 0, S = 0, W = 0, uS = 0, uW = 0, combined = false;
    DFU.forEach(function (f, i) {
      var r = (s.fix && s.fix[i]) || {};
      var q = n0(r.unit) * units + n0(r.extra);
      var d = f[2] * q;
      if (q > 0 && f[3] === 'Combined only') combined = true;
      rows.push({ qty: q, dfu: d });
      T += d;
      if (f[3] === 'Soil') { S += d; uS += f[2] * n0(r.unit); }
      if (f[3] === 'Waste') { W += d; uW += f[2] * n0(r.unit); }
    });
    return { rows: rows, total: T, soil: S, waste: W, unitSoil: uS, unitWaste: uW, combinedWarning: combined };
  }

  function drainSize(r) {
    var dfu = num(r.dfu);
    if (!(dfu > 0)) return {};
    var idx, size;
    if (r.type === 'Stack') {
      if (n0(r.intervals) <= 3) {
        idx = firstAtLeast(DRAIN_ST3, dfu); size = idx < 0 ? 99 : DRAIN_SIZES[idx];
      } else {
        var a = firstAtLeast(DRAIN_STT, dfu), b = firstAtLeast(DRAIN_STI, n0(r.atInterval));
        size = Math.max(a < 0 ? 99 : DRAIN_SIZES[a], b < 0 ? 99 : DRAIN_SIZES[b]);
      }
    } else if (r.type === 'Building drain') {
      var col = BD[String(num(r.slope))];
      if (!col) return { table: '', required: 'Pick slope 1, 2 or 4 %' };
      idx = firstAtLeast(col, dfu); size = idx < 0 ? 99 : BD_SIZES[idx];
    } else {
      idx = firstAtLeast(DRAIN_HB, dfu); size = idx < 0 ? 99 : DRAIN_SIZES[idx];
    }
    var o = { table: size === 99 ? 'Above table' : size };
    if (size === 99) { o.required = 'Above table'; return o; }
    o.required = r.type === 'Building drain' ? Math.max(size, 3) : Math.max(size, r.wc === 'Yes' ? 3 : 0);
    o.dn = dnOf(o.required);
    var sel = num(r.selected);
    if (sel > 0) o.check = sel >= o.required ? (sel > o.required ? 'OK (larger than required)' : 'OK') : 'TOO SMALL';
    var basis = sel > 0 ? sel : o.required;
    var vi = firstAtLeast(VENTS, Math.max(1.25, basis / 2));
    o.vent = vi < 0 ? 'Above table' : VENTS[vi];
    return o;
  }
  function drPipes(s) { return { rows: (s.seg || []).map(drainSize) }; }

  /* ------------------------------------------------------------------ */
  /* RAINWATER                                                           */
  /* ------------------------------------------------------------------ */
  function rnLeaders(s) {
    var i = n0(s.intensity), minL = n0(s.minLeader), A = 0, Q = 0;
    var rows = (s.areas || []).map(function (r) {
      var a = num(r.area), o = {};
      if (!(a > 0)) return o;
      o.ls = n0(r.c) * i * a / 3600;
      o.gpm = o.ls / GPM_LS;
      A += a; Q += o.ls;
      if (num(r.n) > 0) {
        o.per = o.gpm / num(r.n);
        var k = -1;
        for (var j = 0; j < LEADERS.length; j++) if (LEADERS[j][1] >= o.per - 1e-9) { k = j; break; }
        o.table = k < 0 ? 'Above table' : LEADERS[k][0];
        o.required = k < 0 ? 'Above table' : Math.max(LEADERS[k][0], minL);
        if (typeof o.required === 'number') {
          var m = LEADERS.filter(function (L) { return L[0] === o.required; })[0];
          o.dn = m ? m[2] : '';
          var sel = num(r.selected);
          if (sel > 0) o.check = sel >= o.required ? 'OK' : 'TOO SMALL';
        }
      }
      return o;
    });
    return { rows: rows, area: A, ls: Q, gpm: Q / GPM_LS };
  }

  function rnDrains(s) {
    var rows = (s.seg || []).map(function (r) {
      var Q = num(r.q), S = num(r.slope), n = num(r.n), o = {};
      if (!(Q > 0 && S > 0 && n > 0)) return o;
      o.req = Math.pow((Q / 1000) * n * Math.pow(4, 5 / 3) / (Math.PI * Math.sqrt(S / 100)), 3 / 8) * 1000;
      var k = firstAtLeast(STORM_PIPES, o.req);
      if (k < 0) { o.nominal = 'Above list'; return o; }
      o.nominal = STORM_PIPES[k];
      var d = STORM_PIPES[k] / 1000;
      o.cap = (1 / n) * (Math.PI * d * d / 4) * Math.pow(d / 4, 2 / 3) * Math.sqrt(S / 100) * 1000;
      o.v = (o.cap / 1000) / (Math.PI * d * d / 4);
      o.check = o.cap >= Q ? 'OK' : 'TOO SMALL';
      return o;
    });
    return { rows: rows };
  }

  /* ------------------------------------------------------------------ */
  /* FIRE FIGHTING                                                       */
  /* ------------------------------------------------------------------ */
  function riserDemand(n, sprinklered) {
    return Math.min(500 + 250 * (Math.max(1, n0(n)) - 1), sprinklered === 'No' ? 1250 : 1000);
  }

  function fireDemand(s) {
    var h = hazard(s.hazard) || HAZARDS[0];
    var o = { hz: h };
    o.densUS = h[2]; o.dens = h[2] * DENS_SI; o.areaUS = h[3]; o.area = h[3] * FT2_M2;
    o.sprLmin = o.dens * o.area; o.sprLs = o.sprLmin / 60; o.sprGpm = o.sprLmin / GPM_LMIN;
    o.hoseGpm = h[4]; o.hoseLmin = h[4] * GPM_LMIN; o.hoseLs = o.hoseLmin / 60;
    o.totLmin = o.sprLmin + o.hoseLmin; o.totLs = o.totLmin / 60; o.totGpm = o.totLmin / GPM_LMIN;
    o.durRange = h[5] + ' – ' + h[6];
    var dur = n0(s.duration);
    o.durCheck = dur >= h[5] && dur <= h[6] ? 'OK' : 'Outside table range';
    o.nfpaTank = o.totLmin * dur / 1000;
    o.maxCov = h[7] * FT2_M2;
    o.covCheck = n0(s.coverage) <= o.maxCov ? 'OK' : 'TOO LARGE';
    o.qMin = o.dens * n0(s.coverage);
    o.pReq = Math.pow(o.qMin / n0(s.K), 2);
    o.pDes = Math.max(o.pReq, n0(s.pMin));
    o.qDes = n0(s.K) * Math.sqrt(o.pDes);
    o.nSpr = Math.ceil(o.area / n0(s.coverage) - 1e-9);
    o.riserGpm = riserDemand(s.risers, s.sprinklered);
    o.riserHead = n0(s.lvElev) + n0(s.lvFric) + n0(s.lvBar) * BAR_M;
    o.sprHead = n0(s.spElev) + n0(s.spFric) + o.pDes * BAR_M;
    o.pumpGpm = Math.max(o.totGpm, o.riserGpm);
    o.pumpLs = o.pumpGpm * GPM_LMIN / 60;
    o.pumpHead = Math.max(o.riserHead, o.sprHead);
    o.pumpBar = o.pumpHead / BAR_M;
    o.tankMin = Math.max(60, dur);
    o.tank = o.pumpGpm * GPM_LMIN * o.tankMin / 1000;
    return o;
  }

  function fireZones(s) {
    var rows = (s.zones || []).map(function (z) {
      var h = hazard(z.hazard), o = {};
      if (!h) return o;
      o.dens = h[2] * DENS_SI; o.spr = h[2] * h[3]; o.hose = h[4]; o.tot = o.spr + o.hose;
      o.ls = o.tot * GPM_LMIN / 60;
      o.p = Math.max(0.5, Math.pow(o.dens * n0(z.coverage) / n0(z.K), 2));
      if (has(z.elev) && has(z.fric)) o.head = num(z.elev) + num(z.fric) + o.p * BAR_M;
      if (has(z.duration)) {
        o.vol = o.tot * GPM_LMIN * num(z.duration) / 1000;
        o.check = num(z.duration) >= h[5] && num(z.duration) <= h[6] ? 'OK' : 'Outside table range';
      }
      return o;
    });
    var r = { rows: rows };
    r.riserGpm = riserDemand(s.risers, s.sprinklered);
    r.riserHead = n0(s.lvElev) + n0(s.lvFric) + n0(s.lvBar) * BAR_M;
    var maxQ = 0, maxH = 0, maxV = 0;
    rows.forEach(function (o) {
      if (o.tot > maxQ) maxQ = o.tot;
      if (o.head > maxH) maxH = o.head;
      if (o.vol > maxV) maxV = o.vol;
    });
    r.pumpGpm = Math.max(maxQ, r.riserGpm);
    r.pumpLs = r.pumpGpm * GPM_LMIN / 60;
    r.pumpM3h = r.pumpGpm * GPM_LMIN * 60 / 1000;
    r.pumpHead = Math.max(maxH, r.riserHead);
    r.pumpBar = r.pumpHead / BAR_M;
    r.at150 = 0.65 * r.pumpHead;
    r.shutoff = 1.4 * r.pumpHead;
    r.tank = Math.max(maxV, r.pumpGpm * GPM_LMIN * 60 / 1000);
    return r;
  }

  // Branch line, sprinkler by sprinkler, from the remotest sprinkler toward the riser.
  function fireBranch(s) {
    var K = n0(s.K), C = n0(s.C), N = Math.max(1, Math.min(15, Math.round(n0(s.n)))), P = n0(s.p1), Q = 0;
    var rows = [];
    for (var k = 0; k < N; k++) {
      var g = (s.seg && s.seg[k]) || {};
      var d = n0(g.d), L = n0(g.len), Le = n0(g.fit), dz = n0(g.dz);
      var q = K * Math.sqrt(Math.max(P, 0));
      Q += q;
      var p = 6.05e5 * Math.pow(Q, 1.85) / (Math.pow(C, 1.85) * Math.pow(d, 4.87));
      var loss = p * (L + Le);
      var next = P + loss + dz * G / 100;
      var v = (Q / 60000) / areaM2(d);
      var lim = [n0(s.vGood) || 3, n0(s.vPractice) || 6, n0(s.vMax) || 10];
      var chk = v > lim[2] ? 'Too high – upsize' : v > lim[1] ? 'Upsize (above practice limit)' : v > lim[0] ? 'OK' : 'Good';
      rows.push({ i: k + 1, P: P, q: q, Q: Q, d: d, L: L, Le: Le, dz: dz, p: p, loss: loss, next: next, v: v, check: chk });
      P = next;
    }
    var last = rows[rows.length - 1];
    var est = n0(s.density) * n0(s.coverage) * N;
    var maxP = 0, maxV = 0;
    rows.forEach(function (r) { if (r.P > maxP) maxP = r.P; if (r.v > maxV) maxV = r.v; });
    return { rows: rows, Q: last.Q, Qls: last.Q / 60, Qgpm: last.Q / GPM_LMIN, Pin: last.next, PinM: last.next * BAR_M,
      est: est, ratio: est > 0 ? last.Q / est : 0, pRatio: n0(s.p1) > 0 ? maxP / n0(s.p1) : 0, vMax: maxV };
  }

  /* ------------------------------------------------------------------ */
  /* UNITS                                                               */
  /* ------------------------------------------------------------------ */
  var UNITS = {
    flow: { base: 'm³/s', u: [['L/s', 0.001], ['m³/s', 1], ['m³/h', 1 / 3600], ['L/min', 1 / 60000], ['US gpm', 6.30901964e-05], ['Imp gpm', 7.57681667e-05]] },
    pressure: { base: 'kPa', u: [['kPa', 1], ['bar', 100], ['psi', 6.894757293], ['m H2O', 9.80665], ['ft H2O', 2.98906692], ['Pa', 0.001]] },
    length: { base: 'm', u: [['mm', 0.001], ['cm', 0.01], ['m', 1], ['inch', 0.0254], ['ft', 0.3048]] },
    volume: { base: 'm³', u: [['L', 0.001], ['m³', 1], ['US gal', 0.00378541], ['Imp gal', 0.00454609], ['ft³', 0.0283168]] },
    area: { base: 'm²', u: [['m²', 1], ['ft²', 0.092903], ['cm²', 0.0001]] },
    power: { base: 'kW', u: [['kW', 1], ['W', 0.001], ['hp', 0.745700], ['BTU/h', 0.000293071], ['TR', 3.51685]] },
    density: { base: 'mm/min', u: [['mm/min (L/min/m²)', 1], ['gpm/ft²', 40.746]] },
    kfactor: { base: 'L/min/bar^0.5', u: [['L/min/bar^0.5', 1], ['gpm/psi^0.5', 14.40]] }
  };
  function convert(group, value, from) {
    var g = UNITS[group], f = null;
    g.u.forEach(function (u) { if (u[0] === from) f = u[1]; });
    var v = num(value);
    return g.u.map(function (u) { return [u[0], isNaN(v) || f === null ? NaN : v * f / u[1]]; });
  }

  var api = {
    GPM_LS: GPM_LS, GPM_LMIN: GPM_LMIN, BAR_M: BAR_M,
    WSFU: WSFU, HUNTER: HUNTER, HOT_FIXTURES: HOT_FIXTURES, DFU: DFU, LEADERS: LEADERS,
    STORM_PIPES: STORM_PIPES, HAZARDS: HAZARDS, SCH40: SCH40, UNITS: UNITS,
    DRAIN_SIZES: DRAIN_SIZES, BD_SIZES: BD_SIZES, VENTS: VENTS,
    num: num, has: has, hunter: hunter, hazard: hazard,
    wsBuilding: wsBuilding, wsSegments: wsSegments, wsHot: wsHot,
    drFixtures: drFixtures, drPipes: drPipes, drainSize: drainSize,
    rnLeaders: rnLeaders, rnDrains: rnDrains,
    fireDemand: fireDemand, fireZones: fireZones, fireBranch: fireBranch,
    convert: convert
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.Engine = api;
})(typeof window !== 'undefined' ? window : this);
