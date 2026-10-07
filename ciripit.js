/* Ciripit de păsări, generat în browser (Web Audio), fără fișiere audio.
   Pornește la prima atingere a paginii (browserele nu permit sunet înainte de asta)
   și se poate opri din butonul „Ciripit”. Alegerea se ține minte pe dispozitiv. */
(function () {
  "use strict";
  var AC = window.AudioContext || window.webkitAudioContext;
  var btn = document.getElementById("birds");
  if (!AC || !btn) return;

  var PREF = "pineroots-sunet";
  var VOLUME = 0.45;
  var ctx = null, master = null, dryBus = null, verb = null, bed = null;
  var playing = false, timers = [];

  function pref() { try { return localStorage.getItem(PREF); } catch (e) { return null; } }
  function setPref(v) { try { localStorage.setItem(PREF, v); } catch (e) {} }
  function rnd(a, b) { return a + Math.random() * (b - a); }
  function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

  function impulse(seconds, decay) {
    var rate = ctx.sampleRate, len = Math.floor(rate * seconds), buf = ctx.createBuffer(2, len, rate);
    for (var c = 0; c < 2; c++) {
      var d = buf.getChannelData(c);
      for (var i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
    }
    return buf;
  }

  function setup() {
    ctx = new AC();
    ctx.onstatechange = show;
    master = ctx.createGain(); master.gain.value = 0; master.connect(ctx.destination);
    dryBus = ctx.createGain(); dryBus.connect(master);
    verb = ctx.createConvolver(); verb.buffer = impulse(2.4, 3.2);
    var wet = ctx.createGain(); wet.gain.value = 0.5; verb.connect(wet); wet.connect(master);

    /* a very quiet bed of air through leaves */
    var len = ctx.sampleRate * 4, nb = ctx.createBuffer(1, len, ctx.sampleRate), nd = nb.getChannelData(0), last = 0;
    for (var i = 0; i < len; i++) { last = (last + 0.035 * (Math.random() * 2 - 1)) / 1.035; nd[i] = last * 3.2; }
    var src = ctx.createBufferSource(); src.buffer = nb; src.loop = true;
    var bp = ctx.createBiquadFilter(); bp.type = "bandpass"; bp.frequency.value = 900; bp.Q.value = 0.4;
    bed = ctx.createGain(); bed.gain.value = 0.018;
    var lfo = ctx.createOscillator(); lfo.frequency.value = 0.07;
    var lfoGain = ctx.createGain(); lfoGain.gain.value = 0.01;
    lfo.connect(lfoGain); lfoGain.connect(bed.gain);
    src.connect(bp); bp.connect(bed); bed.connect(master);
    src.start(); lfo.start();
  }

  /* one bird somewhere in the forest: its own distance, side and echo */
  function perch(dist) {
    var g = ctx.createGain(); g.gain.value = 0.95 - 0.65 * dist;
    var lp = ctx.createBiquadFilter(); lp.type = "lowpass"; lp.frequency.value = 9500 - 5500 * dist;
    var pan = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
    g.connect(lp);
    var out = lp;
    if (pan) { pan.pan.value = rnd(-0.85, 0.85); lp.connect(pan); out = pan; }
    out.connect(dryBus);
    var send = ctx.createGain(); send.gain.value = 0.18 + 0.5 * dist; out.connect(send); send.connect(verb);
    return g;
  }

  /* a single whistled note: pitch path [[t, Hz], ...] relative to its start */
  function note(bus, t, dur, path, amp, vib) {
    var o = ctx.createOscillator(), e = ctx.createGain();
    o.type = "sine";
    o.frequency.setValueAtTime(path[0][1], t);
    for (var i = 1; i < path.length; i++) o.frequency.exponentialRampToValueAtTime(path[i][1], t + path[i][0] * dur);
    if (vib) {
      var l = ctx.createOscillator(), lg = ctx.createGain();
      l.frequency.value = vib[0]; lg.gain.value = vib[1];
      l.connect(lg); lg.connect(o.frequency); l.start(t); l.stop(t + dur + 0.05);
    }
    var att = Math.min(0.012, dur * 0.25);
    e.gain.setValueAtTime(0, t);
    e.gain.linearRampToValueAtTime(amp, t + att);
    e.gain.linearRampToValueAtTime(amp * 0.6, t + dur * 0.7);
    e.gain.linearRampToValueAtTime(0, t + dur);
    o.connect(e); e.connect(bus);
    o.start(t); o.stop(t + dur + 0.02);
  }

  /* pițigoi: „ti-ciu, ti-ciu, ti-ciu” */
  function tit(t) {
    var bus = perch(rnd(0.15, 0.7)), k = rnd(0.9, 1.1), n = Math.floor(rnd(3, 6));
    for (var i = 0; i < n; i++) {
      var s = t + i * 0.27;
      note(bus, s, 0.085, [[0, 5300 * k], [1, 4700 * k]], 0.22);
      note(bus, s + 0.11, 0.12, [[0, 3700 * k], [1, 3350 * k]], 0.2);
    }
    return n * 0.27 + 0.2;
  }

  /* cinteză: tril care coboară și se încheie cu o fluturare */
  function finch(t) {
    var bus = perch(rnd(0.2, 0.8)), k = rnd(0.92, 1.08), n = Math.floor(rnd(8, 13)), s = t;
    for (var i = 0; i < n; i++) {
      var base = (5400 - (i / n) * 2000) * k;
      note(bus, s, 0.045, [[0, base * 0.72], [1, base]], 0.16);
      s += 1 / rnd(12, 15);
    }
    note(bus, s + 0.04, 0.13, [[0, 2600 * k], [0.6, 5600 * k], [1, 4200 * k]], 0.18);
    note(bus, s + 0.2, 0.09, [[0, 4800 * k], [1, 3100 * k]], 0.14);
    return s - t + 0.4;
  }

  /* mierlă: fraze line, fluierate */
  function blackbird(t) {
    var bus = perch(rnd(0.3, 0.9)), n = Math.floor(rnd(4, 8)), s = t;
    for (var i = 0; i < n; i++) {
      var d = rnd(0.11, 0.26), f = rnd(1700, 3100);
      note(bus, s, d, [[0, f], [0.5, f * rnd(0.88, 1.18)], [1, f * rnd(0.8, 1.1)]], 0.2, [rnd(5, 9), rnd(30, 80)]);
      s += d + rnd(0.04, 0.14);
    }
    return s - t + 0.3;
  }

  /* ciripituri scurte, departe */
  function tweet(t) {
    var bus = perch(rnd(0.6, 1)), n = Math.floor(rnd(1, 4)), s = t;
    for (var i = 0; i < n; i++) {
      var f = rnd(5200, 7000);
      note(bus, s, rnd(0.03, 0.05), [[0, f], [1, f * rnd(0.55, 0.7)]], 0.14);
      s += rnd(0.08, 0.16);
    }
    return s - t;
  }

  var SINGERS = [
    { sing: tit, every: [6, 13], first: [0.4, 1.2] },
    { sing: finch, every: [8, 17], first: [3, 6] },
    { sing: blackbird, every: [10, 20], first: [6, 10] },
    { sing: tweet, every: [2.5, 7], first: [1.5, 3] }
  ];

  function loop(b, wait) {
    timers.push(setTimeout(function () {
      if (!playing) return;
      if (ctx.state === "running") b.sing(ctx.currentTime + 0.05);
      loop(b, rnd(b.every[0], b.every[1]));
    }, wait * 1000));
  }

  function start() {
    if (playing) { if (ctx.resume) ctx.resume(); return; }
    if (!ctx) setup();
    if (ctx.resume) ctx.resume();
    playing = true;
    var now = ctx.currentTime;
    master.gain.cancelScheduledValues(now);
    master.gain.setValueAtTime(master.gain.value, now);
    master.gain.linearRampToValueAtTime(VOLUME, now + 3);
    SINGERS.forEach(function (b) { loop(b, rnd(b.first[0], b.first[1])); });
    show();
  }

  function stop() {
    if (!playing) return;
    playing = false;
    timers.forEach(clearTimeout); timers = [];
    var now = ctx.currentTime;
    master.gain.cancelScheduledValues(now);
    master.gain.setValueAtTime(master.gain.value, now);
    master.gain.linearRampToValueAtTime(0, now + 0.8);
    show();
  }

  function audible() { return playing && ctx && ctx.state === "running"; }
  function show() {
    var on = audible();
    btn.setAttribute("aria-pressed", on ? "true" : "false");
    btn.setAttribute("aria-label", on ? "Oprește ciripitul păsărilor" : "Pornește ciripitul păsărilor");
  }

  btn.hidden = false;
  show();

  btn.addEventListener("click", function (e) {
    e.stopPropagation();
    if (audible()) { stop(); setPref("off"); } else { start(); setPref("on"); detach(); }
  });

  /* first touch, click or key anywhere on the page starts it, unless the guest turned it off */
  /* a scroll alone does not unlock sound, so keep listening until the audio is actually running */
  var EVENTS = ["pointerdown", "pointerup", "touchend", "click", "keydown"];
  function detach() { EVENTS.forEach(function (n) { window.removeEventListener(n, firstGesture, true); }); }
  function firstGesture(e) {
    if (e.target && e.target.closest && e.target.closest("#birds")) return;
    if (pref() === "off") { detach(); return; }
    start();
    if (ctx.state === "running") { detach(); return; }
    var r = ctx.resume && ctx.resume();
    if (r && r.then) r.then(function () { if (ctx.state === "running") detach(); }, function () {});
  }
  if (pref() !== "off") EVENTS.forEach(function (n) { window.addEventListener(n, firstGesture, true); });

  /* quiet when the guest switches apps or tabs */
  document.addEventListener("visibilitychange", function () {
    if (!ctx) return;
    if (document.hidden) { if (ctx.suspend) ctx.suspend(); }
    else if (playing && ctx.resume) ctx.resume();
  });
})();
