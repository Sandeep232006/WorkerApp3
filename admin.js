const $ = id => document.getElementById(id);
const U = JSON.parse(sessionStorage.getItem("u") || "null");
if (!U || U.role !== "admin") location.replace("login.html");
const money = n => "Rs. " + Number(n).toFixed(2), m2 = n => Number(n).toFixed(2);
const esc = s => String(s).replace(/[&<>"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const cap = s => s[0] + s.slice(1).toLowerCase();
const sum = (a, k) => a.reduce((t, r) => t + +r[k], 0);
let W = [], R = [];

if (U) {
  $("who").innerHTML = "Signed in as <b>" + esc(U.wn) + "</b> (" + esc(U.wid) + ") · <a href='#' id='out2'>Log out</a>";
  $("out2").onclick = e => { e.preventDefault(); sessionStorage.clear(); location.href = "login.html"; };
}
async function get(path) {
  const r = await fetch(path + "?token=" + encodeURIComponent(U.token));
  if (!r.ok) { sessionStorage.clear(); location.replace("login.html"); throw new Error("not admin"); }
  return r.json();
}
const badge = (cls, t) => `<span class="b ${cls}">${t}</span>`;
const tbl = (head, rows) => '<div class="tw"><table><thead><tr>' + head.map(h => `<th>${h}</th>`).join("") + "</tr></thead><tbody>" + rows.join("") + "</tbody></table></div>";

function workersPage() {
  const pend = R.filter(r => r.status === "PENDING");
  const cards = [["Workers", W.length], ["Total jobs", R.length], ["Discrepancies", R.filter(r => !r.reconciled).length],
    ["Pending payouts", pend.length], ["Platform fees", money(sum(R, "fee"))], ["Still to receive", money(sum(pend, "expected"))]];
  let h = '<div class="card stats">' + cards.map(([l, v]) => `<div class="stat"><span>${l}</span><b style="font-size:16px">${v}</b></div>`).join("") + "</div><h2>All workers</h2>";
  h += tbl(["Worker ID", "Name", "Phone", "Jobs", "Days worked", "Hours", "Gross (Rs.)", "Fees (Rs.)", "Net (Rs.)", "Received (Rs.)", "Pending", "Discrepancies", ""], W.map(w => {
    const a = R.filter(r => r.wid === w.wid), bad = a.filter(r => !r.reconciled).length;
    return `<tr><td>${esc(w.wid)}</td><td>${esc(w.wn)}</td><td>${esc(w.phone)}</td><td class="n">${a.length}</td><td class="n">${sum(a, "days")}</td><td class="n">${a.some(r => r.hours) ? sum(a, "hours").toFixed(1) : "-"}</td><td class="n">${m2(sum(a, "gross"))}</td><td class="n">${m2(sum(a, "fee"))}</td>
      <td class="n">${m2(sum(a, "expected"))}</td><td class="n">${m2(sum(a, "actual"))}</td><td class="n">${a.filter(r => r.status === "PENDING").length}</td>
      <td>${badge(bad ? "bad" : "ok", bad + (bad === 1 ? " issue" : " issues"))}</td><td><a href="adminpay.html?wid=${encodeURIComponent(w.wid)}">View payments</a></td></tr>`;
  }));
  $("aw").innerHTML = h;
}

function paymentsPage() {
  const pre = new URLSearchParams(location.search).get("wid") || "";
  $("wf").innerHTML = '<option value="">All workers</option>' + W.map(w => `<option value="${esc(w.wid)}">${esc(w.wn)} (${esc(w.wid)})</option>`).join("");
  $("wf").value = pre;
  const draw = () => {
    const q = $("q").value.toLowerCase(), s = $("sf").value, wf = $("wf").value;
    const rows = R.filter(r => (!wf || r.wid === wf) && (!q || [r.wn, r.wid, r.pl, r.jt, r.jid, r.tx].join(" ").toLowerCase().includes(q)) &&
      (!s || (s === "DISC" ? !r.reconciled : r.status === s)));
    let h = `<p class="sub">Showing ${rows.length} of ${R.length} payments · ${rows.filter(r => !r.reconciled).length} with discrepancies</p>`;
    h += rows.length ? tbl(["Worker", "Platform", "Job", "Work period", "Txn", "Gross (Rs.)", "Fee (Rs.)", "Expected (Rs.)", "Actual (Rs.)", "Difference (Rs.)", "Due", "Payout", "Amount", "Result"],
      rows.map(r => `<tr><td>${esc(r.wn)}<small>${esc(r.wid)}</small></td><td>${esc(r.pl)}</td><td>${esc(r.jt)}<small>${esc(r.jid)}</small></td><td>${r.start} to ${r.end}<small>${r.days} days${r.hours ? " · " + r.hours + " h" : ""}</small></td><td>${esc(r.tx)}</td>
        <td class="n">${m2(r.gross)}</td><td class="n">${m2(r.fee)}</td><td class="n">${m2(r.expected)}</td><td class="n">${m2(r.actual)}</td>
        <td class="n ${r.amountState === "MATCHED" ? "pos" : "neg"}">${m2(r.diff)}</td><td>${r.due}</td>
        <td>${badge(r.status === "PAID" ? "ok" : "warn", cap(r.status))}</td><td>${badge(r.amountState === "MATCHED" ? "ok" : "bad", cap(r.amountState))}</td>
        <td>${badge(r.reconciled ? "ok" : "bad", r.reconciled ? "Reconciled" : "Discrepancy")}</td></tr>`)) : '<div class="card empty">No payments match.</div>';
    $("out").innerHTML = h;
  };
  $("q").oninput = $("wf").onchange = $("sf").onchange = draw;
  draw();
}

if (U) Promise.all([get("/api/admin/workers"), get("/api/admin/records")]).then(([w, r]) => {
  W = w; R = r; if ($("aw")) workersPage(); if ($("ap")) paymentsPage();
}).catch(() => {});
