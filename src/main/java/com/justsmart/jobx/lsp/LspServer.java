package com.justsmart.jobx.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cognex Insight Jobx Script 语言服务器（LSP over stdio）。
 *
 * 用法: java -jar cognex-jobx-lsp-server-all.jar [--stdio]
 *
 * 支持的 LSP 能力: 全文同步、补全、悬停、签名帮助、诊断。
 */
public final class LspServer {

    private final InputStream in = System.in;
    private final OutputStream out = System.out;
    private final SymbolDb db = new SymbolDb();
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private boolean shutdown;

    public static void main(String[] args) throws IOException {
        new LspServer().run();
    }

    void run() throws IOException {
        log("Cognex Jobx LSP server started (Java " + System.getProperty("java.version") + ")");
        while (true) {
            String message = readMessage();
            if (message == null) break;
            Object parsed;
            try {
                parsed = Json.parse(message);
            } catch (IllegalArgumentException e) {
                log("bad json: " + e.getMessage());
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) parsed;
            Object id = request.get("id");
            String method = Json.stringValue(request.get("method"));
            if (method != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = request.get("params") instanceof Map<?, ?>
                        ? (Map<String, Object>) request.get("params") : Map.of();
                handle(method, params, id);
                if ("exit".equals(method)) break;
            } else if (request.get("id") != null) {
                // 客户端响应，忽略
            }
            if (shutdown) break;
        }
        log("server stopped");
    }

    private void handle(String method, Map<String, Object> params, Object id) throws IOException {
        switch (method) {
            case "initialize" -> {
                Map<String, Object> caps = new LinkedHashMap<>();
                Map<String, Object> completion = new LinkedHashMap<>();
                completion.put("triggerCharacters", List.of(".", "(", ","));
                completion.put("resolveProvider", false);
                caps.put("completionProvider", completion);
                caps.put("hoverProvider", true);
                Map<String, Object> sig = new LinkedHashMap<>();
                sig.put("triggerCharacters", List.of("(", ","));
                caps.put("signatureHelpProvider", sig);
                caps.put("textDocumentSync", 1); // Full
                Map<String, Object> serverInfo = new LinkedHashMap<>();
                serverInfo.put("name", "cognex-jobx-lsp-server");
                serverInfo.put("version", version());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("capabilities", caps);
                result.put("serverInfo", serverInfo);
                reply(id, result);
            }
            case "initialized" -> { }
            case "shutdown" -> {
                shutdown = true;
                reply(id, null);
            }
            case "exit" -> { }
            case "textDocument/didOpen" -> {
                Map<String, Object> doc = child(params, "textDocument");
                String uri = Json.stringValue(doc.get("uri"));
                String text = Json.stringValue(doc.get("text"));
                if (uri != null) {
                    documents.put(uri, text);
                    publishDiagnostics(uri, text);
                }
            }
            case "textDocument/didChange" -> {
                String uri = Json.stringValue(child(params, "textDocument").get("uri"));
                List<Object> changes = Json.arrayValue(params.get("contentChanges"));
                if (uri != null && !changes.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> last = (Map<String, Object>) changes.get(changes.size() - 1);
                    String text = Json.stringValue(last.get("text"));
                    documents.put(uri, text);
                    publishDiagnostics(uri, text);
                }
            }
            case "textDocument/didClose" -> {
                String uri = Json.stringValue(child(params, "textDocument").get("uri"));
                if (uri != null) documents.remove(uri);
            }
            case "textDocument/hover" -> reply(id, hover(params));
            case "textDocument/completion" -> reply(id, completion(params));
            case "textDocument/signatureHelp" -> reply(id, signatureHelp(params));
            default -> {
                if (id != null) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("code", -32601);
                    err.put("message", "method not supported: " + method);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("jsonrpc", "2.0");
                    resp.put("id", id);
                    resp.put("error", err);
                    send(resp);
                }
            }
        }
    }

    // ---------- hover ----------

    private Object hover(Map<String, Object> params) {
        String text = textOf(params);
        if (text == null) return null;
        int offset = offsetOf(text, params);
        String word = Analysis.wordAt(text, offset);
        SymbolDb.Symbol sym = lookup(word);
        if (sym == null) return null;
        Map<String, Object> contents = new LinkedHashMap<>();
        contents.put("kind", "markdown");
        StringBuilder md = new StringBuilder();
        md.append("```text\n").append(sym.signature()).append("\n```\n\n");
        if (sym.description() != null && !sym.description().isBlank()) {
            md.append(sym.description());
        }
        if (sym.group() != null) md.append("\n\n*").append(sym.group());
        if (sym.category() != null && !sym.category().equals(sym.group())) {
            md.append(" · ").append(sym.category());
        }
        if (sym.group() != null) md.append("*");
        contents.put("value", md.toString());
        Map<String, Object> hover = new LinkedHashMap<>();
        hover.put("contents", contents);
        return hover;
    }

    private SymbolDb.Symbol lookup(String word) {
        if (word == null || word.isBlank()) return null;
        String member = word;
        int dot = word.lastIndexOf('.');
        if (dot >= 0) member = word.substring(dot + 1);
        // gr.xxx → 图形绘制方法；其它成员访问取最后一段
        SymbolDb.Symbol sym = db.scriptApi(member);
        if (sym != null) return sym;
        return db.spreadsheetFunction(member);
    }

    // ---------- completion ----------

    private Object completion(Map<String, Object> params) {
        String text = textOf(params);
        int offset = text == null ? 0 : offsetOf(text, params);
        String prefix = text == null ? "" : Analysis.wordAt(text, offset);
        String member = prefix;
        int dot = prefix.lastIndexOf('.');
        boolean memberAccess = dot >= 0;
        if (memberAccess) member = prefix.substring(dot + 1);

        List<Map<String, Object>> items = new ArrayList<>();
        if (memberAccess) {
            // 成员访问: gr. → 绘制方法；前缀匹配脚本 API 与函数
            for (SymbolDb.Symbol s : db.allScriptApi()) {
                if (s.name().toLowerCase(Locale.ROOT).startsWith(member.toLowerCase(Locale.ROOT))) {
                    items.add(SymbolDb.completionItem(s));
                }
            }
        } else {
            for (SymbolDb.Symbol s : db.allSpreadsheetFunctions()) {
                items.add(SymbolDb.completionItem(s));
            }
            for (SymbolDb.Symbol s : db.allScriptApi()) {
                items.add(SymbolDb.completionItem(s));
            }
            for (String kw : SymbolDb.jsKeywords()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", kw);
                item.put("kind", 14); // Keyword
                items.add(item);
            }
            items.addAll(SymbolDb.snippets());
        }
        boolean isIncomplete = memberAccess;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isIncomplete", isIncomplete);
        result.put("items", items);
        return result;
    }

    // ---------- signature help ----------

    private Object signatureHelp(Map<String, Object> params) {
        String text = textOf(params);
        if (text == null) return null;
        int offset = offsetOf(text, params);
        Analysis.CallContext ctx = Analysis.callContext(text, offset);
        if (ctx == null || ctx.name().isBlank()) return null;
        SymbolDb.Symbol sym = lookup(ctx.name());
        if (sym == null) return null;
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("label", sym.signature());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("kind", "markdown");
        doc.put("value", sym.description() == null ? "" : sym.description());
        label.put("documentation", doc);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signatures", List.of(label));
        result.put("activeSignature", 0);
        result.put("activeParameter", ctx.activeParameter());
        return result;
    }

    // ---------- diagnostics ----------

    private void publishDiagnostics(String uri, String text) throws IOException {
        List<Map<String, Object>> diags = new ArrayList<>();
        for (Analysis.Diagnostic d : Analysis.diagnostics(text)) {
            Map<String, Object> diag = new LinkedHashMap<>();
            Map<String, Object> range = range(d.line(), d.startChar(), d.endChar());
            diag.put("range", range);
            diag.put("severity", d.severity());
            diag.put("source", "cognex-jobx");
            diag.put("message", d.message());
            diags.add(diag);
        }
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("uri", uri);
        note.put("diagnostics", diags);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("method", "textDocument/publishDiagnostics");
        msg.put("params", note);
        send(msg);
    }

    private static Map<String, Object> range(int line, int start, int end) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("start", pos(line, start));
        r.put("end", pos(line, end));
        return r;
    }

    private static Map<String, Object> pos(int line, int ch) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("line", line);
        p.put("character", ch);
        return p;
    }

    // ---------- helpers ----------

    private String textOf(Map<String, Object> params) {
        String uri = Json.stringValue(child(params, "textDocument").get("uri"));
        return uri == null ? null : documents.getOrDefault(uri, "");
    }

    private static Map<String, Object> child(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map<?, ?> mm ? castMap(mm) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /** LSP position (0-based line/character) → 文本 offset。 */
    private static int offsetOf(String text, Map<String, Object> params) {
        Map<String, Object> position = child(params, "position");
        double line = position.get("line") instanceof Number n ? n.doubleValue() : 0;
        double ch = position.get("character") instanceof Number n ? n.doubleValue() : 0;
        int l = (int) line, c = (int) ch;
        int offset = 0, cur = 0;
        while (cur < l && offset < text.length()) {
            int nl = text.indexOf('\n', offset);
            if (nl < 0) return text.length();
            offset = nl + 1;
            cur++;
        }
        return Math.min(offset + c, text.length());
    }

    // ---------- wire ----------

    private String readMessage() throws IOException {
        int contentLength = -1;
        StringBuilder headers = new StringBuilder();
        int b;
        boolean seenHeader = false;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int b2 = in.read();
                if (b2 == '\n') {
                    String line = headers.toString();
                    headers.setLength(0);
                    if (line.isEmpty()) {
                        if (seenHeader) break;
                        continue;
                    }
                    seenHeader = true;
                    int colon = line.indexOf(':');
                    if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                    }
                } else {
                    headers.append((char) b).append((char) b2);
                }
            } else if (b == '\n') {
                String line = headers.toString();
                headers.setLength(0);
                if (line.isEmpty()) {
                    if (seenHeader) break;
                    continue;
                }
                seenHeader = true;
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                }
            } else {
                headers.append((char) b);
            }
        }
        if (b == -1) return null;
        if (contentLength < 0) throw new IOException("missing Content-Length header");
        byte[] body = in.readNBytes(contentLength);
        return new String(body, StandardCharsets.UTF_8);
    }

    private void reply(Object id, Object result) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id == null ? Map.of() : id);
        resp.put("result", result == null ? Map.of() : result);
        send(resp);
    }

    private void send(Map<String, Object> message) throws IOException {
        byte[] body = Json.write(message).getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        }
    }

    private static void log(String msg) {
        System.err.println("[cognex-jobx-lsp] " + msg);
    }

    private static String version() {
        String v = LspServer.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
