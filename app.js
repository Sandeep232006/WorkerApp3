const U = JSON.parse(sessionStorage.getItem("u") || "null");
if (!U) location.replace("login.html");

const $ = id => document.getElementById(id);
const rs = n => "Rs. " + Number(n).toFixed(2);
const esc = s => String(s).replace(/[&<>"]/g, m => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[m]));
const cap = s => s[0] + s.slice(1).toLowerCase();
const F = ["jid","jt","pl","gr","ft","fv","tx","sd","ed","hh","due","pd","ac"];
let recs = [], editing = null;

async function api(method, url, body) {
  if (method === "POST") body.set("token", U.token);
  else url += (url.includes("?") ? "&" : "?") + "token=" + encodeURIComponent(U.token);
  const res = await fetch(url, {method, body});
  const d = await res.json();
  if (!res.ok) throw new Error(d.error || "Request failed.");
  recs = d; render();
}

function shown() {
  const q = $("q").value.toLowerCase(), s = $("sf").value;
  return recs.map((r, i) => [r, i]).filter(([r]) =>
    (!q || [r.wn, r.wid, r.jt, r.pl, r.tx, r.jid].join(" ").toLowerCase().includes(q)) &&
    (!s || (s === "DISC" ? !r.reconciled : r.status === s)));
}

function render() { if ($("list")) list(); if ($("ins")) insights(); if ($("sch")) sched(); }

function list() {
  const n = k => recs.filter(k).length;
  const st = [["Records", recs.length], ["Discrepancies", n(r => !r.reconciled)], ["Underpaid", n(r => r.amountState === "UNDERPAID")],
    ["Overpaid", n(r => r.amountState === "OVERPAID")], ["Late", n(r => r.status === "LATE")], ["Pending", n(r => r.status === "PENDING")]];
  const rows = shown();
  let h = '<div class="card stats">' + st.map(([l, v]) => `<div class="stat"><span>${l}</span><b>${v}</b></div>`).join("") + "</div>";
  h += `<h2>Payment reconciliation <small>(${rows.length} of ${recs.length})</small></h2>`;
  if (!rows.length) h += '<div class="card empty">No records match.</div>';
  if (rows.length) {
    const m = n => Number(n).toFixed(2);
    const th = ["#", "Platform", "Job", "Work period", "Txn", "Gross (Rs.)", "Fee (Rs.)", "Expected (Rs.)", "Actual (Rs.)", "Difference (Rs.)", "Payout", "Amount", "Result", ""];
    h += '<div class="tw"><table><thead><tr>' + th.map(t => `<th>${t}</th>`).join("") + "</tr></thead><tbody>";
    rows.forEach(([r, i]) => {
      h += `<tr><td>${i + 1}</td>
        <td>${esc(r.pl)}</td><td>${esc(r.jt)}<small>${esc(r.jid)}</small></td>
        <td>${r.start} to ${r.end}<small>${r.days} days${r.hours ? " · " + r.hours + " h" : ""}</small></td><td>${esc(r.tx)}</td>
        <td class="n">${m(r.gross)}</td><td class="n">${m(r.fee)}</td><td class="n">${m(r.expected)}</td><td class="n">${m(r.actual)}</td>
        <td class="n ${r.amountState === "MATCHED" ? "pos" : "neg"}">${m(r.diff)}</td>
        <td><span class="b ${r.status === "PAID" ? "ok" : "warn"}">${cap(r.status)}</span></td>
        <td><span class="b ${r.amountState === "MATCHED" ? "ok" : "bad"}">${cap(r.amountState)}</span></td>
        <td><span class="b ${r.reconciled ? "ok" : "bad"}">${r.reconciled ? "Reconciled" : "Discrepancy"}</span></td>
        <td><button class="rm" data-e="${i}">Edit</button> <button class="rm" data-i="${i}">Remove</button></td></tr>`;
    });
    h += "</tbody></table></div>";
  }
  $("list").innerHTML = h;
}

function bars(title, rows, fmt) {
  const max = Math.max(1, ...rows.map(r => r[1]));
  return `<h2>${title}</h2><div class="card chart">` + rows.map(([l, v, c]) =>
    `<div class="bar"><span>${esc(l)}</span><i><u style="width:${v / max * 100}%;background:var(${c || "--navy"})"></u></i><b>${fmt(v)}</b></div>`).join("") + "</div>";
}

function insights() {
  const sum = (k, f = () => true) => recs.filter(f).reduce((a, r) => a + +r[k], 0);
  const plat = {}, earn = {};
  recs.forEach(r => { plat[r.pl] = (plat[r.pl] || 0) + +r.fee; earn[r.pl] = (earn[r.pl] || 0) + +r.expected; });
  const cnt = s => recs.filter(r => r.status === s).length;
  const cards = [["Gross earnings", sum("gross")], ["Platform fees", sum("fee")], ["Net earnings", sum("expected")],
    ["Received so far", sum("actual")], ["Still to receive", sum("expected", r => r.status === "PENDING")],
    ["Paid vs expected", sum("diff", r => r.status !== "PENDING")],
    ["Days worked", String(sum("days"))], ["Hours worked", recs.some(r => r.hours) ? sum("hours").toFixed(1) : "not recorded"]];
  let h = '<div class="card stats">' + cards.map(([l, v]) => `<div class="stat"><span>${l}</span><b style="font-size:16px">${typeof v === "number" ? rs(v) : v}</b></div>`).join("") + "</div>";
  h += bars("Payout status", [["Paid", cnt("PAID"), "--ok"], ["Late", cnt("LATE"), "--warn"], ["Pending", cnt("PENDING"), "--bad"]], v => v);
  h += bars("Net earnings by platform", Object.entries(earn), rs) + bars("Platform fees by platform", Object.entries(plat), rs);
  $("ins").innerHTML = h;
}

function sched() {
  const today = new Date().toLocaleDateString("en-CA");
  const days = d => Math.round((Date.parse(d) - Date.parse(today)) / 864e5);
  const card = (r, badges) => `<article class="card rec"><div class="no" style="width:auto;padding:0 10px">${r.due.slice(5)}</div><div style="flex:1;min-width:0">
    <h3>${esc(r.jt)} · ${esc(r.pl)}</h3><p class="num">Expected ${rs(r.expected)} · Txn ${esc(r.tx)} · due ${r.due}</p><p class="num">Worked ${r.start} to ${r.end} · ${r.days} days${r.hh ? " · " + r.hh + " h/day" : ""}</p><div class="badges">${badges}</div></div></article>`;
  const pend = recs.filter(r => r.status === "PENDING").sort((a, b) => a.due.localeCompare(b.due));
  const over = pend.filter(r => r.due < today), next = pend.filter(r => r.due >= today);
  const done = recs.filter(r => r.status !== "PENDING").sort((a, b) => b.pd.localeCompare(a.pd));
  const sec = (t, a, empty) => `<h2>${t}</h2>` + (a.length ? a.join("") : `<div class="card empty">${empty}</div>`);
  $("sch").innerHTML =
    sec("Overdue: not paid yet", over.map(r => card(r, `<span class="b bad">${-days(r.due)} day(s) overdue</span>`)), "Nothing overdue.") +
    sec("Coming up", next.map(r => card(r, `<span class="b ${days(r.due) <= 3 ? "warn" : "ok"}">${days(r.due) === 0 ? "due today" : "due in " + days(r.due) + " day(s)"}</span>`)), "No upcoming payouts.") +
    sec("Already paid", done.map(r => card(r, `<span class="b ${r.status === "PAID" ? "ok" : "warn"}">${r.status === "PAID" ? "Paid on time" : "Paid late"} (${r.pd})</span>
      <span class="b ${r.amountState === "MATCHED" ? "ok" : "bad"}">${cap(r.amountState)}</span>`)), "No payouts received yet.");
}

// ---- Overview page only ----
if ($("list")) {
$("q").oninput = $("sf").onchange = list;
$("list").onclick = e => {
  const d = e.target.dataset;
  if (d.i !== undefined) api("DELETE", "/api/records?i=" + d.i);
  if (d.e !== undefined) edit(+d.e);
};

function edit(i) { location.href = "index.html?edit=" + i; }
if ($("imp")) $("imp").onchange = async e => {
  const f = e.target.files[0]; if (!f) return;
  try { await api("POST", "/api/records?import=1", await f.text()); $("msg").textContent = "Imported " + recs.length + " records (replaced the previous list)."; }
  catch (x) { $("msg").textContent = "Import failed. " + x.message; }
  e.target.value = "";
};

if ($("exp")) $("exp").onclick = () => {
  const cols = ["wid","wn","jid","jt","pl","gross","fee","expected","actual","diff","status","amountState","reconciled"];
  const q = v => '"' + String(v).replace(/"/g, '""') + '"';
  const csv = [cols.join(",")].concat(recs.map(r => cols.map(c => q(r[c])).join(","))).join("\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([csv], {type: "text/csv"}));
  a.download = "reconciliation_report.csv"; a.click();
};

}

// ---- Add record page only ----
if ($("f")) {
function fillForm(i) {
  const r = recs[i];
  const v = {wid: r.wid, wn: r.wn, jid: r.jid, jt: r.jt, pl: r.pl, gr: r.gross, ft: r.ft, fv: r.fv, tx: r.tx, sd: r.start, ed: r.end, hh: r.hh, due: r.due, pd: r.pd, ac: r.pd ? r.actual : ""};
  F.forEach(k => $(k).value = v[k]);
  editing = i; $("sub").textContent = "Update record"; $("ft").onchange(); duration();
}

function preview() {
  const g = +$("gr").value || 0, v = +$("fv").value || 0;
  $("prev").textContent = "Expected payout: " + rs(g - ($("ft").value === "p" ? g * v / 100 : v));
}
$("ft").onchange = () => { $("fl").textContent = $("ft").value === "p" ? "Platform fee rate (%)" : "Platform fee rate (%)"; preview(); };
["gr", "fv"].forEach(k => $(k).oninput = preview);

// Shows the number of days between the start date and the end date (both days count).
function duration() {
  const a = Date.parse($("sd").value), b = Date.parse($("ed").value);
  $("dur").textContent = (isNaN(a) || isNaN(b) || b < a) ? "0 days" : (Math.round((b - a) / 864e5) + 1) + " days";
}
["sd", "ed"].forEach(k => $(k).onchange = duration);

// The platforms and job titles that the workers already use are offered as suggestions in the form.
fetch("/api/lists?token=" + encodeURIComponent(U.token)).then(r => r.json()).then(d => {
  $("pls").innerHTML = d.platforms.map(p => `<option value="${esc(p)}">`).join("");
  $("jts").innerHTML = d.titles.map(t => `<option value="${esc(t)}">`).join("");
}).catch(() => {});

$("f").onsubmit = async e => {
  e.preventDefault();
  const body = new URLSearchParams(Object.fromEntries(F.map(k => [k, $(k).value.trim()])));
  if (editing !== null) body.set("edit", editing);
  try {
    await api("POST", "/api/records", body);
    editing = null; $("sub").textContent = "Save record"; $("err").textContent = "";
    location.href = "overview.html";
  } catch (x) { $("err").textContent = x.message; }
};

window.onLoaded = () => {
  const e = new URLSearchParams(location.search).get("edit");
  if (e !== null && recs[+e]) fillForm(+e);
};
}

if (U) {
  $("who").innerHTML = "Signed in as <b>" + esc(U.wn) + "</b> (" + esc(U.wid) + ") · <a href='#' id='out'>Log out</a>";
  $("out").onclick = e => { e.preventDefault(); sessionStorage.clear(); location.href = "login.html"; };
  fetch("/api/records?token=" + encodeURIComponent(U.token))
    .then(r => r.ok ? r.json() : Promise.reject()).then(d => { recs = d; render(); if (window.onLoaded) onLoaded(); })
    .catch(() => { sessionStorage.clear(); location.replace("login.html"); });
}
