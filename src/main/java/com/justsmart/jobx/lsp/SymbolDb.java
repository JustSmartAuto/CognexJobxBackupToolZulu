package com.justsmart.jobx.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 符号数据库：电子表格函数（functions.json）与脚本 API（scriptApi.json）。
 */
public final class SymbolDb {

    public record Symbol(String name, String kind, String signature, String description,
                         String group, String category) {}

    /** 电子表格函数，按名称索引（全小写）。 */
    private final Map<String, Symbol> spreadsheetFunctions = new TreeMap<>();
    /** 脚本 API，按名称索引（全小写）。 */
    private final Map<String, Symbol> scriptApi = new TreeMap<>();

    public SymbolDb() {
        loadFunctions();
        loadScriptApi();
    }

    public Symbol spreadsheetFunction(String name) {
        return spreadsheetFunctions.get(name.toLowerCase(Locale.ROOT));
    }

    public Symbol scriptApi(String name) {
        return scriptApi.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Symbol> allSpreadsheetFunctions() {
        return List.copyOf(spreadsheetFunctions.values());
    }

    public List<Symbol> allScriptApi() {
        return List.copyOf(scriptApi.values());
    }

    private void loadFunctions() {
        List<Object> items = Json.arrayValue(Json.parse(resource("functions.json")));
        for (Object item : items) {
            Map<String, Object> m = (Map<String, Object>) item;
            String name = Json.stringValue(m.get("name"));
            spreadsheetFunctions.put(name.toLowerCase(Locale.ROOT), new Symbol(
                    name,
                    Json.stringValue(m.get("category")),
                    Json.stringValue(m.get("signature")),
                    Json.stringValue(m.get("description")),
                    "电子表格函数",
                    Json.stringValue(m.get("category"))));
        }
    }

    private void loadScriptApi() {
        List<Object> items = Json.arrayValue(Json.parse(resource("scriptApi.json")));
        for (Object item : items) {
            Map<String, Object> m = (Map<String, Object>) item;
            String name = Json.stringValue(m.get("name"));
            scriptApi.put(name.toLowerCase(Locale.ROOT), new Symbol(
                    name,
                    Json.stringValue(m.get("kind")),
                    Json.stringValue(m.get("signature")),
                    Json.stringValue(m.get("description")),
                    Json.stringValue(m.get("group")),
                    "脚本 API"));
        }
    }

    private static String resource(String name) {
        try (InputStream in = SymbolDb.class.getResourceAsStream("/" + name)) {
            if (in == null) throw new IllegalStateException("缺少资源: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 构建补全项（LSP CompletionItem，用 Map 表示）。 */
    static Map<String, Object> completionItem(Symbol s) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", s.name());
        item.put("kind", completionKind(s.kind()));
        item.put("detail", s.signature());
        String doc = s.description() == null || s.description().isBlank()
                ? s.signature()
                : "**" + s.signature() + "**\n\n" + s.description()
                  + (s.category() != null ? "\n\n*类别: " + s.category() + "*" : "");
        Map<String, Object> docs = new LinkedHashMap<>();
        docs.put("kind", "markdown");
        docs.put("value", doc);
        item.put("documentation", docs);
        return item;
    }

    private static int completionKind(String kind) {
        if (kind == null) return 1; // Text
        return switch (kind) {
            case "Class" -> 7;
            case "Method" -> 2;
            case "Property" -> 10;
            case "Function" -> 3;
            default -> 3; // Function（电子表格函数）
        };
    }

    /** JS 关键字补全。 */
    static List<String> jsKeywords() {
        return List.of("break", "case", "catch", "continue", "default", "delete", "do",
                "else", "finally", "for", "function", "if", "in", "instanceof", "new",
                "return", "switch", "this", "throw", "try", "typeof", "var", "void",
                "while", "with", "null", "true", "false", "undefined");
    }

    /** 常用代码片段。 */
    static List<Map<String, Object>> snippets() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(snippet("script-template", "Script 完整模板",
                "\"use strict\";\n\nfunction ${1:Script}() {\n    this.${2:state} = ${3:0};\n}\n\n"
                + "module.exports = ${1:Script};\n\n"
                + "${1:Script}.prototype.run = function(${4}) {\n    $0\n    return this.${2:state};\n};\n\n"
                + "${1:Script}.prototype.draw = function(gr) {\n};\n\n"
                + "${1:Script}.prototype.save = function() {\n    return { \"${2:state}\": this.${2:state} };\n};\n\n"
                + "${1:Script}.prototype.load = function(saved) {\n    this.${2:state} = saved.${2:state};\n};"));
        out.add(snippet("draw-fn", "draw 方法",
                "${1:Script}.prototype.draw = function(gr) {\n    gr.plotPoint(${2:x}, ${3:y}, \"${4:name}\", 0x00FF00);\n};"));
        out.add(snippet("save-load", "save/load 方法",
                "${1:Script}.prototype.save = function() {\n    return { \"${2:key}\": this.${2:key} };\n};\n\n"
                + "${1:Script}.prototype.load = function(saved) {\n    this.${2:key} = saved.${2:key};\n};"));
        return out;
    }

    private static Map<String, Object> snippet(String name, String detail, String body) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("label", name);
        s.put("kind", 15); // Snippet
        s.put("detail", detail);
        s.put("insertTextFormat", 2);
        s.put("insertText", body);
        return s;
    }
}
