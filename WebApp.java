import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

// Worker-facing web app. All money logic comes from YOUR classes in GigEconomySystem.java
// (Job, Transaction, ReconciliationRecord), used without any change.
public class WebApp {

    static class Entry { String wid, jt, pl, rate, due, pd, hh; WorkPeriod period; Job job; Transaction tx; }

    static final Map<String, String[]> workers = new LinkedHashMap<>();   // workerId -> {name, phone, passwordHash}
    static final List<Entry> entries = new ArrayList<>();
    static final String ADMIN_ID = System.getenv().getOrDefault("ADMIN_ID", "Sandeep");
    static final String ADMIN_HASH = sha(System.getenv().getOrDefault("ADMIN_PASSWORD", "23042006"));  // when hosting, set ADMIN_ID / ADMIN_PASSWORD instead of editing code
    static final Set<String> admins = new HashSet<>();              // tokens of signed-in admins
    static final Map<String, String> sessions = new HashMap<>();          // login token -> workerId

    public static void main(String[] args) throws IOException {
        String pw = sha("23042006");                      // every worker account uses this password
        if (Files.exists(Path.of("dataset/gig_workers_100.csv")) && Files.exists(Path.of("dataset/gig_payments.csv"))) {
            loadDataset(pw);                              // creates accounts W1 to W100 and their payment records
        } else {                                          // small sample if the dataset folder is missing
        workers.put("W1", new String[]{"Ravi Kumar", "9876543210", pw});
        workers.put("W2", new String[]{"Meena Rao", "9123456780", pw});
        }
        String listen = System.getenv("X_ZOHO_CATALYST_LISTEN_PORT");      // Zoho Catalyst AppSail tells the app its port here
        if (listen == null || listen.isEmpty()) listen = System.getenv().getOrDefault("PORT", "8080");
        int port = Integer.parseInt(listen);
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/api", WebApp::api);
        s.createContext("/", WebApp::file);
        s.start();
        System.out.println("Worker app running. Open http://localhost:" + port);
    }

    // Reads the two CSV files of the dataset folder: one account per worker, one record per payment.
    static void loadDataset(String pw) throws IOException {
        List<String> wl = Files.readAllLines(Path.of("dataset/gig_workers_100.csv"), StandardCharsets.UTF_8);
        for (String line : wl.subList(1, wl.size())) {
            if (line.isBlank()) continue;
            String[] c = csv(line);
            workers.put(c[0], new String[]{c[1], c[2], pw});          // worker_id, name, phone, hashed password
        }
        List<String> pl = Files.readAllLines(Path.of("dataset/gig_payments.csv"), StandardCharsets.UTF_8);
        String[] head = csv(pl.get(0).replace("\uFEFF", ""));
        for (String line : pl.subList(1, pl.size())) {
            if (line.isBlank()) continue;
            String[] c = csv(line);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < head.length; i++) row.put(head[i].trim(), c[i]);
            Map<String, String> f = new HashMap<>();
            f.put("wid", row.get("worker_id")); f.put("jid", row.get("job_id")); f.put("jt", row.get("job_title")); f.put("pl", row.get("platform"));
            f.put("sd", pick(row, "start_date", "job_start")); f.put("ed", pick(row, "end_date", "job_end")); f.put("hh", pick(row, "hours_per_day"));
            f.put("gr", row.get("gross_amount")); f.put("fv", row.get("fee_rate_percent"));
            f.put("tx", row.get("transaction_id")); f.put("due", row.get("scheduled_date")); f.put("pd", row.get("paid_date")); f.put("ac", row.get("actual_payout"));
            entries.add(build(f, null));                              // same checks and same Job/Transaction objects as the form
        }
    }

    // Returns the first column that exists, so a file that calls the dates start_date and end_date or job_start and job_end works either way.
    static String pick(Map<String, String> row, String... names) {
        for (String n : names) if (row.get(n) != null) return row.get(n);
        return "";
    }

    // The platforms and job titles that the workers already use, most used first. The Add job form offers them as suggestions.
    static String lists() {
        Map<String, Integer> pl = new HashMap<>(), jt = new HashMap<>();
        for (Entry e : entries) { pl.merge(e.pl, 1, Integer::sum); jt.merge(e.jt, 1, Integer::sum); }
        return "{\"platforms\":" + top(pl) + ",\"titles\":" + top(jt) + "}";
    }

    static String top(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(counts.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("[");
        for (Map.Entry<String, Integer> x : l) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(x.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    // Splits one CSV line and keeps the commas that are inside double quotes.
    static String[] csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) { out.add(sb.toString()); sb.setLength(0); }
            else sb.append(ch);
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    static synchronized void api(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath(), m = ex.getRequestMethod();
            Map<String, String> p = form(ex.getRequestURI().getRawQuery());
            if (m.equals("POST")) p.putAll(form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (p.containsKey("wid")) p.put("wid", p.get("wid").trim().toUpperCase());   // worker ids are not case sensitive (w1 = W1)
            if (path.equals("/api/register")) { register(p); send(ex, 200, "application/json", "{\"ok\":true}".getBytes()); return; }
            if (path.equals("/api/login") && p.getOrDefault("wid", "").equalsIgnoreCase(ADMIN_ID)) {
                if (!sha(p.getOrDefault("pw", "")).equals(ADMIN_HASH)) { error(ex, 401, "Wrong worker ID or password."); return; }
                String token = UUID.randomUUID().toString();
                admins.add(token);
                send(ex, 200, "application/json", obj("name", "Administrator", "role", "admin", "token", token).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/api/login")) {
                String[] w = workers.get(p.getOrDefault("wid", ""));
                if (w == null || !w[2].equals(sha(p.getOrDefault("pw", "")))) { error(ex, 401, "Wrong worker ID or password."); return; }
                String token = UUID.randomUUID().toString();
                sessions.put(token, p.get("wid"));
                send(ex, 200, "application/json", (("{" + kv("name", w[0]) + kv("role", "worker") + kv("token", token)).replaceAll(",$", "") + "}").getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/api/lists")) {                      // suggestions for the Add job form (any signed in person)
                String t = p.getOrDefault("token", "");
                if (!sessions.containsKey(t) && !admins.contains(t)) { error(ex, 401, "Please sign in again."); return; }
                send(ex, 200, "application/json", lists().getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.startsWith("/api/admin/")) {                 // admin-only: sees every worker
                if (!admins.contains(p.getOrDefault("token", ""))) { error(ex, 401, "Admin access only."); return; }
                String out;
                if (path.equals("/api/admin/records")) out = json(entries, true);
                else if (path.equals("/api/admin/workers")) {
                    StringBuilder sb = new StringBuilder("[");
                    for (Map.Entry<String, String[]> en : workers.entrySet()) {
                        if (sb.length() > 1) sb.append(",");
                        sb.append(obj("wid", en.getKey(), "wn", en.getValue()[0], "phone", en.getValue()[1]));
                    }
                    out = sb.append("]").toString();
                } else { error(ex, 404, "Not found."); return; }
                send(ex, 200, "application/json", out.getBytes(StandardCharsets.UTF_8));
                return;
            }
            String sid = sessions.get(p.getOrDefault("token", ""));
            if (sid == null) { error(ex, 401, "Please sign in again."); return; }
            p.put("wid", sid);
            List<Entry> mine = mine(p.get("wid"));
            if (m.equals("POST")) {
                Entry old = p.containsKey("edit") ? mine.get(Integer.parseInt(p.get("edit"))) : null;
                Entry e = build(p, old);
                if (old == null) entries.add(e); else entries.set(entries.indexOf(old), e);
            } else if (m.equals("DELETE")) entries.remove(mine.get(Integer.parseInt(p.get("i"))));
            send(ex, 200, "application/json", json(p.get("wid")).getBytes(StandardCharsets.UTF_8));
        } catch (DateTimeParseException e) { error(ex, 400, "Invalid date.");
        } catch (NumberFormatException e) { error(ex, 400, "Enter valid numbers.");
        } catch (RuntimeException e) { error(ex, 400, String.valueOf(e.getMessage())); }
    }

    static void register(Map<String, String> p) {
        String id = p.getOrDefault("wid", "").trim(), name = p.getOrDefault("wn", "").trim(), ph = p.getOrDefault("phone", "").trim(), pw = p.getOrDefault("pw", "");
        if (id.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("Enter your worker ID and name.");
        if (id.equalsIgnoreCase(ADMIN_ID)) throw new IllegalArgumentException("That worker ID is already taken.");
        if (!ph.matches("\\d{10}")) throw new IllegalArgumentException("Enter a 10-digit phone number.");
        if (pw.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (workers.containsKey(id)) throw new IllegalArgumentException("That worker ID is already taken.");
        workers.put(id, new String[]{name, ph, sha(pw)});
    }

    // Validates the form and builds the objects from your classes.
    static Entry build(Map<String, String> f, Entry ignore) {
        for (String k : new String[]{"wid", "jid", "jt", "pl", "tx"})
            if (f.getOrDefault(k, "").isBlank()) throw new IllegalArgumentException("Fill in every text field.");
        String wid = f.get("wid").trim(), jid = f.get("jid").trim(), tx = f.get("tx").trim();
        double gross = Double.parseDouble(f.get("gr")), rate = Double.parseDouble(f.get("fv"));
        if (gross <= 0) throw new IllegalArgumentException("Gross amount must be greater than 0.");
        if (rate < 0 || rate > 100) throw new IllegalArgumentException("Platform fee rate must be between 0 and 100.");
        for (String k : new String[]{"sd", "ed"})
            if (f.getOrDefault(k, "").isBlank()) throw new IllegalArgumentException("Enter the work start date and the work end date.");
        WorkPeriod period = new WorkPeriod(LocalDate.parse(f.get("sd")), LocalDate.parse(f.get("ed")));
        String hhText = f.getOrDefault("hh", "").trim();                       // hours per day is optional
        if (!hhText.isEmpty()) {
            double hh = Double.parseDouble(hhText);
            if (hh <= 0 || hh > 16) throw new IllegalArgumentException("Hours per day must be more than 0 and at most 16.");
        }
        LocalDate due = LocalDate.parse(f.get("due"));
        if (due.isBefore(period.getEnd())) throw new IllegalArgumentException("The payout cannot be due before the job ends.");
        String pdText = f.getOrDefault("pd", "");
        LocalDate paid = pdText.isBlank() ? null : LocalDate.parse(pdText);
        if (paid != null && f.getOrDefault("ac", "").isBlank()) throw new IllegalArgumentException("Enter the actual payout for a paid job.");
        double actual = paid == null ? 0 : Double.parseDouble(f.get("ac"));
        for (Entry o : entries) {
            if (o == ignore) continue;
            if (o.tx.toFileString().startsWith(tx + ",")) throw new IllegalArgumentException("Transaction ID already used.");
            if (o.wid.equals(wid) && o.job.getJobId().equals(jid)) throw new IllegalArgumentException("You already have a job with that Job ID.");
            if (o.wid.equals(wid) && o.period.overlaps(period))
                throw new IllegalArgumentException("This job overlaps with job " + o.job.getJobId() + " (" + o.period.getStart() + " to " + o.period.getEnd()
                        + "). A worker cannot work on two jobs on the same day.");
        }
        Entry e = new Entry();
        e.wid = wid; e.jt = f.get("jt").trim(); e.pl = f.get("pl").trim(); e.rate = f.get("fv"); e.due = due.toString(); e.pd = paid == null ? "" : paid.toString(); e.period = period; e.hh = hhText;
        e.job = new Job(jid, e.jt, e.pl, gross, rate);
        e.tx = new Transaction(tx, wid, jid, actual, "JOB_PAYOUT", due, paid);
        return e;
    }

    static List<Entry> mine(String wid) {
        List<Entry> l = new ArrayList<>();
        for (Entry e : entries) if (e.wid.equals(wid)) l.add(e);
        return l;
    }

    static String json(String wid) { return json(mine(wid), false); }

    static String json(List<Entry> list, boolean admin) {
        StringBuilder sb = new StringBuilder("[");
        for (Entry e : list) {
            ReconciliationRecord r = new ReconciliationRecord(e.wid, e.job.getJobId(), e.job.calculateNetEarning(), e.tx.getAmount(), e.tx.getPayoutStatus());
            double d = r.getDifference(); if (Math.abs(d) < 0.005) d = 0;
            if (sb.length() > 1) sb.append(",");
            sb.append("{").append(admin ? kv("wid", e.wid) + kv("wn", workers.get(e.wid)[0]) : "").append(kv("jid", e.job.getJobId())).append(kv("jt", e.jt)).append(kv("pl", e.pl)).append(kv("tx", e.tx.toFileString().split(",")[0]))
              .append(kv("gross", money(e.job.getGrossAmount()))).append(kv("fee", money(e.job.calculatePlatformFee())))
              .append(kv("expected", money(e.job.calculateNetEarning()))).append(kv("actual", money(e.tx.getAmount()))).append(kv("diff", money(d)))
              .append(kv("status", e.tx.getPayoutStatus())).append(kv("amountState", r.isAmountReconciled() ? "MATCHED" : d < 0 ? "UNDERPAID" : "OVERPAID"))
              .append(kv("ft", "p")).append(kv("fv", e.rate)).append(kv("due", e.due)).append(kv("pd", e.pd))
              .append(kv("start", e.period.getStart().toString())).append(kv("end", e.period.getEnd().toString())).append(kv("days", String.valueOf(e.period.getDays())))
              .append(kv("hh", e.hh)).append(kv("hours", e.hh.isEmpty() ? "" : String.format(Locale.US, "%.1f", e.period.getDays() * Double.parseDouble(e.hh))))
              .append("\"reconciled\":").append(r.isReconciled()).append("}");
        }
        return sb.append("]").toString();
    }

    // Passwords are stored as SHA-256 hashes, never as plain text.
    static String sha(String s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String money(double d) { return String.format(Locale.US, "%.2f", d); }
    static String obj(String... kv) {                      // {"k":"v",...} from alternating key/value arguments
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) sb.append(kv(kv[i], kv[i + 1]));
        return "{" + sb.toString().replaceAll(",$", "") + "}";
    }

    static String kv(String k, String v) { return "\"" + k + "\":\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\","; }

    static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        if (body == null) return m;
        for (String p : body.split("&")) {
            String[] kv = p.split("=", 2);
            m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }

    static void file(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        if (p.equals("/")) p = "/login.html";
        if (!p.matches("/[a-z]+\\.(html|css|js)") || !Files.exists(Path.of(p.substring(1)))) { send(ex, 404, "text/plain", "Not found".getBytes()); return; }
        String type = p.endsWith(".css") ? "text/css" : p.endsWith(".js") ? "application/javascript" : "text/html; charset=utf-8";
        send(ex, 200, type, Files.readAllBytes(Path.of(p.substring(1))));
    }

    static void error(HttpExchange ex, int code, String msg) throws IOException {
        send(ex, code, "application/json", ("{" + kv("error", msg).replaceAll(",$", "") + "}").getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}

// The days on which a worker worked on one job. Two jobs of the same worker must never share a day.
class WorkPeriod {
    private final LocalDate start;
    private final LocalDate end;

    WorkPeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new IllegalArgumentException("The end date cannot be before the start date.");
        this.start = start;
        this.end = end;
    }

    LocalDate getStart() { return start; }
    LocalDate getEnd() { return end; }

    // Duration in days, counting both the first and the last day.
    long getDays() { return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1; }

    boolean overlaps(WorkPeriod other) {
        return !end.isBefore(other.start) && !other.end.isBefore(start);
    }
}
