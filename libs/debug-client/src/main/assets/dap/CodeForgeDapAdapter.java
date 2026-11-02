/*
 * CodeForge DAP-Adapter (JDI). Spricht das Debug Adapter Protocol ueber stdin/stdout und
 * steuert eine JVM per JDI/JDWP. Keine Fremdabhaengigkeiten; benoetigt ein JDK mit Modul jdk.jdi (ab Java 11).
 *
 *   launch : startet "java -agentlib:jdwp=...suspend=y" selbst und haengt sich per Socket an.
 *   attach : haengt sich an einen vorhandenen JDWP-Port (z. B. 127.0.0.1:5005 oder adb forward).
 *
 * Wichtig: stdout gehoert dem Protokoll. Diagnose geht ausschliesslich nach stderr.
 */
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CodeForgeDapAdapter {

    // ------------------------------------------------------------------ Mini-JSON
    static final class Json {
        private final String s; private int p;
        private Json(String s) { this.s = s; }
        static Object parse(String text) { Json j = new Json(text); j.ws(); Object v = j.value(); return v; }
        private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
        private Object value() {
            ws(); char c = s.charAt(p);
            if (c == '{') { p++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (s.charAt(p) == '}') { p++; return m; }
                while (true) { ws(); String k = str(); ws(); p++; m.put(k, value()); ws();
                    char d = s.charAt(p++); if (d == '}') return m; } }
            if (c == '[') { p++; List<Object> l = new ArrayList<>(); ws();
                if (s.charAt(p) == ']') { p++; return l; }
                while (true) { l.add(value()); ws(); char d = s.charAt(p++); if (d == ']') return l; } }
            if (c == '"') return str();
            if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
            if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
            if (s.startsWith("null", p)) { p += 4; return null; }
            int st = p; while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
            String n = s.substring(st, p);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            return Long.parseLong(n);
        }
        private String str() {
            p++; StringBuilder b = new StringBuilder();
            while (true) { char c = s.charAt(p++);
                if (c == '"') return b.toString();
                if (c == '\\') { char e = s.charAt(p++);
                    switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                        default: b.append(e); } }
                else b.append(c); } }
        static String write(Object o) { StringBuilder b = new StringBuilder(); w(b, o); return b.toString(); }
        @SuppressWarnings("unchecked")
        private static void w(StringBuilder b, Object o) {
            if (o == null) b.append("null");
            else if (o instanceof String) q(b, (String) o);
            else if (o instanceof Map) { b.append('{'); boolean f = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) { if (!f) b.append(','); f = false; q(b, e.getKey()); b.append(':'); w(b, e.getValue()); }
                b.append('}'); }
            else if (o instanceof List) { b.append('['); boolean f = true;
                for (Object x : (List<Object>) o) { if (!f) b.append(','); f = false; w(b, x); } b.append(']'); }
            else b.append(o.toString());
        }
        private static void q(StringBuilder b, String v) {
            b.append('"');
            for (int i = 0; i < v.length(); i++) { char c = v.charAt(i);
                switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                    default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); } }
            b.append('"');
        }
    }

    @SuppressWarnings("unchecked") static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked") static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    static Map<String, Object> map(Object... kv) { Map<String, Object> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]); return m; }
    static String str(Object o) { return o == null ? null : o.toString(); }
    static int num(Object o, int def) { return o instanceof Number ? ((Number) o).intValue() : def; }

    // ------------------------------------------------------------------ Zustand
    private final InputStream in = new BufferedInputStream(System.in);
    private final OutputStream out = new BufferedOutputStream(System.out);
    private final AtomicInteger seq = new AtomicInteger(1);

    private VirtualMachine vm;
    private Process launched;
    private volatile boolean disconnected;
    private final Map<String, List<Bp>> breakpointsByPath = new LinkedHashMap<>();
    private final Map<String, String> pathBySourceName = new HashMap<>();
    private final Map<Integer, StackFrame> frames = new HashMap<>();
    private final Map<Integer, Object> refs = new HashMap<>();
    private final AtomicInteger idGen = new AtomicInteger(1000);
    private final Map<String, String> packageCache = new HashMap<>();
    private int bpIds = 1;

    static final class Bp { int id; int line; String condition; String logMessage; boolean verified;
        final List<BreakpointRequest> requests = new ArrayList<>(); }

    public static void main(String[] args) throws Exception { new CodeForgeDapAdapter().run(); }

    // ------------------------------------------------------------------ Protokoll-IO
    private void run() throws Exception {
        while (true) {
            String msg = readMessage();
            if (msg == null) break;
            Map<String, Object> req;
            try { req = obj(Json.parse(msg)); } catch (Exception e) { log("Ungueltige Nachricht: " + e); continue; }
            if (!"request".equals(req.get("type"))) continue;
            handle(req);
            if (disconnected) break;
        }
        shutdown();
    }

    private String readMessage() throws IOException {
        int len = -1; StringBuilder line = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) return null;
            if (c == '\n') {
                String l = line.toString().trim(); line.setLength(0);
                if (l.isEmpty()) { if (len >= 0) break; else continue; }
                if (l.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Integer.parseInt(l.substring(15).trim());
            } else line.append((char) c);
        }
        byte[] buf = new byte[len]; int off = 0;
        while (off < len) { int r = in.read(buf, off, len - off); if (r < 0) return null; off += r; }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private synchronized void send(Map<String, Object> m) {
        try {
            byte[] body = Json.write(m).getBytes(StandardCharsets.UTF_8);
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body); out.flush();
        } catch (IOException e) { log("Senden fehlgeschlagen: " + e); }
    }
    private void event(String name, Map<String, Object> body) { send(map("seq", seq.getAndIncrement(), "type", "event", "event", name, "body", body == null ? map() : body)); }
    private void respond(Map<String, Object> req, boolean ok, Map<String, Object> body, String message) {
        Map<String, Object> r = map("seq", seq.getAndIncrement(), "type", "response", "request_seq", req.get("seq"), "success", ok, "command", req.get("command"));
        if (message != null) r.put("message", message);
        if (body != null) r.put("body", body);
        send(r);
    }
    private void output(String category, String text) { event("output", map("category", category, "output", text)); }
    private static void log(String s) { System.err.println("[dap-adapter] " + s); }

    // ------------------------------------------------------------------ Requests
    private void handle(Map<String, Object> req) {
        String cmd = str(req.get("command")); Map<String, Object> a = obj(req.get("arguments"));
        try {
            switch (cmd) {
                case "initialize":
                    respond(req, true, map("supportsConfigurationDoneRequest", true, "supportsConditionalBreakpoints", true,
                        "supportsLogPoints", true, "supportsEvaluateForHovers", false, "supportsTerminateRequest", true), null);
                    break;
                case "launch": doLaunch(req, a); break;
                case "attach": doAttach(req, a); break;
                case "setBreakpoints": doSetBreakpoints(req, a); break;
                case "setExceptionBreakpoints": respond(req, true, map("breakpoints", new ArrayList<>()), null); break;
                case "configurationDone": respond(req, true, map(), null); if (vm != null) resumeAll(); break;
                case "threads": doThreads(req); break;
                case "stackTrace": doStackTrace(req, a); break;
                case "scopes": doScopes(req, a); break;
                case "variables": doVariables(req, a); break;
                case "continue": resumeAll(); respond(req, true, map("allThreadsContinued", true), null); break;
                case "next": doStep(req, a, StepRequest.STEP_OVER); break;
                case "stepIn": doStep(req, a, StepRequest.STEP_INTO); break;
                case "stepOut": doStep(req, a, StepRequest.STEP_OUT); break;
                case "pause": doPause(req); break;
                case "evaluate": doEvaluate(req, a); break;
                case "disconnect": case "terminate":
                    respond(req, true, map(), null); disconnected = true; break;
                default: respond(req, false, null, "Nicht unterstuetzt: " + cmd);
            }
        } catch (Throwable t) {
            log("Fehler bei " + cmd + ": " + t);
            respond(req, false, null, t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    // ------------------------------------------------------------------ Verbindung
    private void doLaunch(Map<String, Object> req, Map<String, Object> a) throws Exception {
        String mainClass = str(a.get("mainClass"));
        if (mainClass == null || mainClass.isEmpty()) { respond(req, false, null, "mainClass fehlt"); return; }
        String javaHome = str(a.get("javaHome"));
        String javaBin = (javaHome != null && !javaHome.isEmpty() ? javaHome : System.getProperty("java.home")) + File.separator + "bin" + File.separator + "java";
        int port; try (ServerSocket ss = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) { port = ss.getLocalPort(); }
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:" + port);
        for (Object o : arr(a.get("vmArgs"))) cmd.add(o.toString());
        List<Object> cp = arr(a.get("classPaths"));
        if (!cp.isEmpty()) { StringBuilder sb = new StringBuilder(); for (Object o : cp) { if (sb.length() > 0) sb.append(File.pathSeparator); sb.append(o); } cmd.add("-cp"); cmd.add(sb.toString()); }
        cmd.add(mainClass);
        for (Object o : arr(a.get("args"))) cmd.add(o.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        String cwd = str(a.get("cwd")); if (cwd != null && !cwd.isEmpty()) pb.directory(new File(cwd));
        launched = pb.start();
        pump(launched.getInputStream(), "stdout"); pump(launched.getErrorStream(), "stderr");
        connect("127.0.0.1", port, 15000);
        respond(req, true, map(), null);
        event("initialized", map());
    }

    private void doAttach(Map<String, Object> req, Map<String, Object> a) throws Exception {
        String host = a.get("hostName") != null ? str(a.get("hostName")) : "127.0.0.1";
        int port = num(a.get("port"), 5005); int timeout = num(a.get("timeout"), 10000);
        connect(host, port, timeout);
        respond(req, true, map(), null);
        event("initialized", map());
    }

    private void pump(InputStream is, String category) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String l; while ((l = r.readLine()) != null) { if (l.startsWith("Listening for transport")) continue; output(category, l + "\n"); }
            } catch (IOException ignored) { }
        }, "pump-" + category); t.setDaemon(true); t.start();
    }

    private void connect(String host, int port, int timeoutMs) throws Exception {
        AttachingConnector conn = null;
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors()) if ("com.sun.jdi.SocketAttach".equals(c.name())) conn = c;
        if (conn == null) throw new IllegalStateException("JDI SocketAttach-Connector fehlt (JDK ohne jdk.jdi?)");
        Map<String, Connector.Argument> args = conn.defaultArguments();
        args.get("hostname").setValue(host); args.get("port").setValue(String.valueOf(port));
        long end = System.currentTimeMillis() + timeoutMs; Exception last = null;
        while (System.currentTimeMillis() < end) {
            try { vm = conn.attach(args); break; } catch (IOException e) { last = e; Thread.sleep(150); }
        }
        if (vm == null) throw new IOException("Kein JDWP auf " + host + ":" + port + " (" + (last == null ? "Timeout" : last.getMessage()) + ")");
        ClassPrepareRequest cpr = vm.eventRequestManager().createClassPrepareRequest();
        cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL); cpr.enable();
        Thread t = new Thread(this::eventLoop, "jdi-events"); t.setDaemon(true); t.start();
    }

    // ------------------------------------------------------------------ Breakpoints
    private void doSetBreakpoints(Map<String, Object> req, Map<String, Object> a) {
        String path = str(obj(a.get("source")).get("path"));
        List<Map<String, Object>> result = new ArrayList<>();
        synchronized (breakpointsByPath) {
            List<Bp> old = breakpointsByPath.remove(path);
            if (old != null && vm != null) for (Bp b : old) for (BreakpointRequest r : b.requests) try { vm.eventRequestManager().deleteEventRequest(r); } catch (Exception ignored) { }
            List<Bp> fresh = new ArrayList<>();
            for (Object o : arr(a.get("breakpoints"))) {
                Map<String, Object> m = obj(o); Bp b = new Bp();
                b.id = bpIds++; b.line = num(m.get("line"), 0); b.condition = str(m.get("condition")); b.logMessage = str(m.get("logMessage"));
                fresh.add(b);
            }
            breakpointsByPath.put(path, fresh);
            pathBySourceName.put(new File(path).getName(), path);
            if (vm != null) for (ReferenceType rt : vm.allClasses()) resolveFor(rt);
            for (Bp b : fresh) result.add(map("id", b.id, "verified", b.verified, "line", b.line));
        }
        respond(req, true, map("breakpoints", result), null);
    }

    private void resolveFor(ReferenceType rt) {
        String src; try { src = rt.sourceName(); } catch (AbsentInformationException e) { return; } catch (Exception e) { return; }
        synchronized (breakpointsByPath) {
            for (Map.Entry<String, List<Bp>> e : breakpointsByPath.entrySet()) {
                String path = e.getKey();
                if (!new File(path).getName().equals(src)) continue;
                String pkg = packageOf(path); String cn = rt.name(); int dot = cn.lastIndexOf('.');
                String rtPkg = dot < 0 ? "" : cn.substring(0, dot);
                if (!pkg.equals(rtPkg)) continue;
                for (Bp b : e.getValue()) {
                    try {
                        for (Location loc : rt.locationsOfLine(b.line)) {
                            boolean exists = false;
                            for (BreakpointRequest r : b.requests) if (r.location().equals(loc)) exists = true;
                            if (exists) continue;
                            BreakpointRequest r = vm.eventRequestManager().createBreakpointRequest(loc);
                            r.setSuspendPolicy(EventRequest.SUSPEND_ALL); r.putProperty("bp", b); r.enable(); b.requests.add(r);
                            if (!b.verified) { b.verified = true; event("breakpoint", map("reason", "changed", "breakpoint", map("id", b.id, "verified", true, "line", b.line))); }
                        }
                    } catch (AbsentInformationException | ClassNotPreparedException ignored) { }
                }
            }
        }
    }

    private String packageOf(String path) {
        return packageCache.computeIfAbsent(path, p -> {
            try (BufferedReader r = Files.newBufferedReader(Paths.get(p), StandardCharsets.UTF_8)) {
                String l; int n = 0;
                while ((l = r.readLine()) != null && n++ < 200) {
                    l = l.trim();
                    if (l.startsWith("package ")) return l.substring(8).replace(";", "").trim();
                }
            } catch (Exception ignored) { }
            return "";
        });
    }

    // ------------------------------------------------------------------ Events
    private void eventLoop() {
        EventQueue q = vm.eventQueue();
        try {
            while (!disconnected) {
                EventSet set = q.remove(); boolean resume = true;
                for (Event e : set) {
                    if (e instanceof ClassPrepareEvent) resolveFor(((ClassPrepareEvent) e).referenceType());
                    else if (e instanceof BreakpointEvent) { if (!onBreakpoint((BreakpointEvent) e)) continue; resume = false; }
                    else if (e instanceof StepEvent) {
                        vm.eventRequestManager().deleteEventRequest(e.request());
                        stopped("step", ((StepEvent) e).thread()); resume = false;
                    } else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) { terminated(); return; }
                }
                if (resume) set.resume();
            }
        } catch (VMDisconnectedException e) { terminated();
        } catch (Throwable t) { log("Event-Loop: " + t); terminated(); }
    }

    private boolean onBreakpoint(BreakpointEvent e) {
        Bp b = (Bp) e.request().getProperty("bp");
        if (b != null) {
            try {
                if (b.condition != null && !b.condition.trim().isEmpty() && !Boolean.TRUE.equals(evalCondition(e.thread(), b.condition))) return false;
                if (b.logMessage != null && !b.logMessage.isEmpty()) { output("console", interpolate(e.thread(), b.logMessage) + "\n"); return false; }
            } catch (Exception ex) { log("Bedingung: " + ex); }
        }
        stopped("breakpoint", e.thread());
        return true;
    }

    private void stopped(String reason, ThreadReference t) {
        invalidate();
        event("stopped", map("reason", reason, "threadId", t.uniqueID(), "allThreadsStopped", true));
    }

    private boolean terminatedSent;
    private synchronized void terminated() {
        if (terminatedSent) return; terminatedSent = true;
        int code = 0; if (launched != null) try { code = launched.waitFor(); } catch (Exception ignored) { }
        event("exited", map("exitCode", code)); event("terminated", map());
    }

    private void invalidate() { frames.clear(); refs.clear(); }
    private void resumeAll() { invalidate(); try { vm.resume(); } catch (VMDisconnectedException ignored) { } }

    // ------------------------------------------------------------------ Threads / Stack / Variablen
    private ThreadReference thread(long id) { for (ThreadReference t : vm.allThreads()) if (t.uniqueID() == id) return t; return null; }

    private void doThreads(Map<String, Object> req) {
        List<Object> l = new ArrayList<>();
        for (ThreadReference t : vm.allThreads()) l.add(map("id", t.uniqueID(), "name", t.name()));
        respond(req, true, map("threads", l), null);
    }

    private void doStackTrace(Map<String, Object> req, Map<String, Object> a) throws Exception {
        ThreadReference t = thread(((Number) a.get("threadId")).longValue());
        List<Object> out = new ArrayList<>();
        if (t != null && t.isSuspended()) {
            for (StackFrame f : t.frames()) {
                int id = idGen.incrementAndGet(); frames.put(id, f);
                Location loc = f.location(); Map<String, Object> src = null;
                try { String n = loc.sourceName(); String p = pathBySourceName.get(n);
                    src = p != null ? map("name", n, "path", p) : map("name", n); } catch (AbsentInformationException ignored) { }
                Map<String, Object> fr = map("id", id, "name", loc.declaringType().name() + "." + loc.method().name(), "line", Math.max(loc.lineNumber(), 0), "column", 1);
                if (src != null) fr.put("source", src);
                out.add(fr);
            }
        }
        respond(req, true, map("stackFrames", out, "totalFrames", out.size()), null);
    }

    private void doScopes(Map<String, Object> req, Map<String, Object> a) {
        int fid = num(a.get("frameId"), -1); StackFrame f = frames.get(fid);
        List<Object> sc = new ArrayList<>();
        if (f != null) {
            int r = idGen.incrementAndGet(); refs.put(r, f);
            sc.add(map("name", "Locals", "variablesReference", r, "expensive", false));
            ObjectReference th = f.thisObject();
            if (th != null) { int r2 = idGen.incrementAndGet(); refs.put(r2, th); sc.add(map("name", "this", "variablesReference", r2, "expensive", false)); }
        }
        respond(req, true, map("scopes", sc), null);
    }

    private void doVariables(Map<String, Object> req, Map<String, Object> a) {
        Object c = refs.get(num(a.get("variablesReference"), -1)); List<Object> vars = new ArrayList<>();
        try {
            if (c instanceof StackFrame) {
                StackFrame f = (StackFrame) c;
                try { for (LocalVariable lv : f.visibleVariables()) vars.add(variable(lv.name(), lv.typeName(), f.getValue(lv))); }
                catch (AbsentInformationException e) { vars.add(map("name", "(keine Debug-Infos, mit -g kompilieren)", "value", "", "variablesReference", 0)); }
            } else if (c instanceof ArrayReference) {
                ArrayReference ar = (ArrayReference) c; int n = Math.min(ar.length(), 200);
                for (int i = 0; i < n; i++) { Value v = ar.getValue(i); vars.add(variable("[" + i + "]", v == null ? "null" : v.type().name(), v)); }
            } else if (c instanceof ObjectReference) {
                ObjectReference o = (ObjectReference) c;
                for (Field fl : o.referenceType().allFields()) { if (fl.isStatic()) continue; vars.add(variable(fl.name(), fl.typeName(), o.getValue(fl))); }
            }
        } catch (Exception e) { log("variables: " + e); }
        respond(req, true, map("variables", vars), null);
    }

    private Map<String, Object> variable(String name, String type, Value v) {
        int ref = 0;
        if ((v instanceof ObjectReference) && !(v instanceof StringReference)) { ref = idGen.incrementAndGet(); refs.put(ref, v); }
        return map("name", name, "value", format(v), "type", type, "variablesReference", ref);
    }

    static String format(Value v) {
        if (v == null) return "null";
        if (v instanceof StringReference) return "\"" + ((StringReference) v).value() + "\"";
        if (v instanceof ArrayReference) { ArrayReference a = (ArrayReference) v; return a.referenceType().name().replace("[]", "") + "[" + a.length() + "]"; }
        if (v instanceof ObjectReference) return ((ObjectReference) v).referenceType().name() + "@" + ((ObjectReference) v).uniqueID();
        return v.toString();
    }

    // ------------------------------------------------------------------ Steuerung
    private void doStep(Map<String, Object> req, Map<String, Object> a, int depth) {
        ThreadReference t = thread(((Number) a.get("threadId")).longValue());
        if (t != null) {
            StepRequest sr = vm.eventRequestManager().createStepRequest(t, StepRequest.STEP_LINE, depth);
            sr.setSuspendPolicy(EventRequest.SUSPEND_ALL); sr.addCountFilter(1); sr.enable();
        }
        respond(req, true, map(), null); resumeAll();
    }

    private void doPause(Map<String, Object> req) {
        vm.suspend(); respond(req, true, map(), null);
        List<ThreadReference> ts = vm.allThreads();
        stopped("pause", ts.isEmpty() ? null : ts.get(0));
    }

    // ------------------------------------------------------------------ Auswertung (einfach: Variablen, Feldpfade, Literale, Vergleiche)
    private void doEvaluate(Map<String, Object> req, Map<String, Object> a) {
        StackFrame f = frames.get(num(a.get("frameId"), -1));
        String ex = str(a.get("expression")); if (ex == null) ex = "";
        try {
            Value v = f == null ? null : resolve(f, ex.trim());
            int ref = 0; if ((v instanceof ObjectReference) && !(v instanceof StringReference)) { ref = idGen.incrementAndGet(); refs.put(ref, v); }
            respond(req, true, map("result", format(v), "variablesReference", ref), null);
        } catch (Exception e) { respond(req, false, null, e.getMessage()); }
    }

    private Value resolve(StackFrame f, String expr) throws Exception {
        if (expr.equals("this")) return f.thisObject();
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) return vm.mirrorOf(expr.substring(1, expr.length() - 1));
        if (expr.matches("-?\\d+")) return vm.mirrorOf(Integer.parseInt(expr));
        if (expr.equals("true") || expr.equals("false")) return vm.mirrorOf(Boolean.parseBoolean(expr));
        if (expr.equals("null")) return null;
        String[] parts = expr.split("\\.");
        Value cur = null; boolean first = true;
        for (String raw : parts) {
            String name = raw; Integer idx = null;
            int b = raw.indexOf('[');
            if (b > 0 && raw.endsWith("]")) { name = raw.substring(0, b); idx = Integer.parseInt(raw.substring(b + 1, raw.length() - 1)); }
            if (first) {
                LocalVariable lv = null; try { lv = f.visibleVariableByName(name); } catch (AbsentInformationException ignored) { }
                if (lv != null) cur = f.getValue(lv);
                else if (f.thisObject() != null) cur = field(f.thisObject(), name);
                else throw new IllegalArgumentException("Unbekannt: " + name);
                first = false;
            } else {
                if (!(cur instanceof ObjectReference)) throw new IllegalArgumentException("Kein Objekt vor ." + name);
                if (name.equals("length") && cur instanceof ArrayReference) cur = vm.mirrorOf(((ArrayReference) cur).length());
                else cur = field((ObjectReference) cur, name);
            }
            if (idx != null) cur = ((ArrayReference) cur).getValue(idx);
        }
        return cur;
    }

    private Value field(ObjectReference o, String name) {
        Field fl = o.referenceType().fieldByName(name);
        if (fl == null) throw new IllegalArgumentException("Feld nicht gefunden: " + name);
        return o.getValue(fl);
    }

    private Boolean evalCondition(ThreadReference t, String cond) throws Exception {
        StackFrame f = t.frame(0);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s*(==|!=|<=|>=|<|>)\\s*(.+)$").matcher(cond.trim());
        if (!m.matches()) { Value v = resolve(f, cond.trim()); return v instanceof BooleanValue && ((BooleanValue) v).value(); }
        Value l = resolve(f, m.group(1).trim()), r = resolve(f, m.group(3).trim());
        String op = m.group(2);
        if (l instanceof PrimitiveValue && r instanceof PrimitiveValue && !(l instanceof BooleanValue)) {
            double x = ((PrimitiveValue) l).doubleValue(), y = ((PrimitiveValue) r).doubleValue();
            switch (op) { case "==": return x == y; case "!=": return x != y; case "<": return x < y; case ">": return x > y; case "<=": return x <= y; default: return x >= y; }
        }
        boolean eq = Objects.equals(format(l), format(r));
        return op.equals("==") ? eq : op.equals("!=") ? !eq : Boolean.FALSE;
    }

    private String interpolate(ThreadReference t, String msg) throws Exception {
        StackFrame f = t.frame(0); StringBuilder sb = new StringBuilder(); int i = 0;
        while (i < msg.length()) {
            int s = msg.indexOf('{', i);
            if (s < 0) { sb.append(msg.substring(i)); break; }
            int e = msg.indexOf('}', s); if (e < 0) { sb.append(msg.substring(i)); break; }
            sb.append(msg, i, s);
            try { sb.append(format(resolve(f, msg.substring(s + 1, e).trim()))); } catch (Exception ex) { sb.append('?'); }
            i = e + 1;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Ende
    private void shutdown() {
        try { if (vm != null) { try { vm.dispose(); } catch (Exception ignored) { } } } finally {
            if (launched != null) launched.destroy();
            terminated();
        }
    }
}
