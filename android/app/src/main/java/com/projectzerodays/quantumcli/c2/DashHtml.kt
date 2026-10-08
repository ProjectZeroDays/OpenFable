package com.projectzerodays.quantumcli.c2

/**
 * QUANTUMDASH — the dashboard served at GET /dash (parity with the Python
 * DASH_HTML). Dark cyan terminal theme; polls /dash/api/state every 3 s.
 */
object DashHtml {

    val HTML: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>QUANTUMDASH</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: "JetBrains Mono", "Fira Mono", Consolas, "Courier New", monospace;
    background: linear-gradient(160deg, #020712 0%, #020308 100%);
    background-attachment: fixed;
    color: #cfefff;
    min-height: 100vh;
    padding: 20px;
  }
  .wrap { max-width: 1100px; margin: 0 auto; }
  header { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; margin-bottom: 18px; }
  h1 {
    font-size: 22px; letter-spacing: 3px; color: #00e5ff;
    text-shadow: 0 0 14px rgba(0,229,255,0.45);
  }
  .sub { color: #4f6f86; font-size: 12px; }
  .badge {
    margin-left: auto; font-size: 12px; padding: 4px 10px; border-radius: 4px;
    border: 1px solid #ff4b6b; color: #ff4b6b; letter-spacing: 1px;
  }
  .badge.off { border-color: #4f6f86; color: #4f6f86; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
  .card {
    background: rgba(0,20,40,0.9);
    border: 1px solid rgba(0,229,255,0.22);
    border-radius: 8px;
    padding: 14px;
    box-shadow: 0 0 18px rgba(0,229,255,0.07), inset 0 0 30px rgba(0,229,255,0.02);
  }
  .card h2 {
    font-size: 12px; letter-spacing: 2px; color: #9fd6ee;
    border-bottom: 1px solid rgba(0,229,255,0.18); padding-bottom: 6px; margin-bottom: 10px;
  }
  .row { display: flex; justify-content: space-between; font-size: 12px; padding: 3px 0; }
  .row .k { color: #4f6f86; }
  .row .v { color: #cfefff; text-align: right; word-break: break-all; }
  .log { max-height: 300px; overflow-y: auto; font-size: 11px; }
  .log .line { padding: 2px 0; border-bottom: 1px dashed rgba(79,111,134,0.25); }
  .tag { color: #00e5ff; }
  .ts { color: #4f6f86; margin-right: 6px; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; }
  .chip {
    font-size: 11px; color: #00e5ff; border: 1px solid rgba(0,229,255,0.35);
    background: rgba(0,229,255,0.06); border-radius: 3px; padding: 3px 7px;
  }
  .empty { color: #4f6f86; font-size: 12px; font-style: italic; }
  a { color: #00e5ff; text-decoration: none; }
  footer { margin-top: 18px; color: #4f6f86; font-size: 11px; text-align: center; }
  ::-webkit-scrollbar { width: 6px; } ::-webkit-scrollbar-thumb { background: #14324a; border-radius: 3px; }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>QUANTUMDASH</h1>
    <span class="sub" id="ver">v?</span>
    <span class="badge off" id="ks">KILLSWITCH OFF</span>
  </header>
  <div class="grid">
    <div class="card">
      <h2>IMPLANTS</h2>
      <div id="implants" class="log"><div class="empty">no implants beaconing yet</div></div>
    </div>
    <div class="card">
      <h2>DECISIONS</h2>
      <div id="decisions" class="log"><div class="empty">no decisions yet</div></div>
    </div>
    <div class="card">
      <h2>AUDIT LOG</h2>
      <div id="audit" class="log"><div class="empty">audit ring empty</div></div>
    </div>
    <div class="card">
      <h2>MITRE ATT&amp;CK COVERAGE</h2>
      <div id="mitre" class="chips"><span class="empty">no coverage yet</span></div>
    </div>
  </div>
  <footer>
    <a href="https://github.com/projectzerodays/Quantum-CLI" target="_blank">projectzerodays/Quantum-CLI</a>
    &nbsp;&middot;&nbsp; authorized security lab use only
  </footer>
</div>
<script>
  function el(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  function render(state) {
    el("ver").textContent = "v" + (state.version || "?");
    var ks = el("ks");
    if (state.killswitch) {
      ks.textContent = "KILLSWITCH ARMED";
      ks.className = "badge";
    } else {
      ks.textContent = "KILLSWITCH OFF";
      ks.className = "badge off";
    }
    var imp = el("implants");
    var implants = state.implants || {};
    var keys = Object.keys(implants);
    imp.innerHTML = keys.length
      ? keys.map(function (h) {
          return '<div class="line"><span class="tag">' + esc(h) + "</span> last_seen " +
            esc(implants[h].last_seen) + " &middot; beacons " + implants[h].beacons + "</div>";
        }).join("")
      : '<div class="empty">no implants beaconing yet</div>';
    var dec = el("decisions");
    var decisions = state.decisions || [];
    dec.innerHTML = decisions.length
      ? decisions.slice().reverse().map(function (d) {
          return '<div class="line"><span class="ts">' + esc(d.ts) + "</span><b>" + esc(d.action) +
            "</b> " + esc(d.target || "-") + " &mdash; " + esc(d.reason || "") + "</div>";
        }).join("")
      : '<div class="empty">no decisions yet</div>';
    var aud = el("audit");
    var audit = state.audit || [];
    aud.innerHTML = audit.length
      ? audit.slice().reverse().map(function (a) {
          return '<div class="line"><span class="ts">' + esc(a.ts) + '</span> <span class="tag">[' +
            esc(a.tag) + "]</span> " + esc(a.msg) + "</div>";
        }).join("")
      : '<div class="empty">audit ring empty</div>';
    var mit = el("mitre");
    var coverage = state.mitre || [];
    mit.innerHTML = coverage.length
      ? coverage.map(function (t) { return '<span class="chip">' + esc(t) + "</span>"; }).join("")
      : '<span class="empty">no coverage yet</span>';
  }
  function tick() {
    fetch("/dash/api/state")
      .then(function (r) { return r.json(); })
      .then(render)
      .catch(function () {});
  }
  tick();
  setInterval(tick, 3000);
</script>
</body>
</html>
""".trimIndent()
}
