package com.justsmart.jobx.lsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 脚本源码分析：诊断、词法提取、调用上下文。 */
public final class Analysis {

    private Analysis() {}

    private static final Set<String> GR_METHODS = Set.of(
            "plotarc", "plotcircle", "plotcross", "plotline", "plotpoint",
            "plotregion", "plotstring", "plotshape");
    private static final Set<String> BUILTIN_MODULES = Set.of("fs", "cognex");
    private static final Pattern GR_CALL = Pattern.compile("\\bgr\\s*\\.\\s*([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern REQUIRE_CALL = Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PROTOTYPE_RUN = Pattern.compile("\\.prototype\\s*\\.\\s*run\\s*=");
    private static final Pattern MODULE_EXPORTS = Pattern.compile("\\bmodule\\s*\\.\\s*exports");

    public record Diagnostic(int line, int startChar, int endChar, int severity, String message) {}

    /** severity: 1 = Error, 2 = Warning */
    public static List<Diagnostic> diagnostics(String text) {
        List<Diagnostic> out = new ArrayList<>();
        balanceCheck(text, out);
        if (MODULE_EXPORTS.matcher(text).find() && !PROTOTYPE_RUN.matcher(text).find()) {
            out.add(new Diagnostic(0, 0, 1, 2,
                    "脚本缺少必需的生命周期方法 Script.prototype.run = function(...) {...}"));
        }
        Matcher gr = GR_CALL.matcher(text);
        while (gr.find()) {
            String method = gr.group(1);
            if (!GR_METHODS.contains(method.toLowerCase())) {
                out.add(position(text, gr.start(1), gr.end(1), 2,
                        "未知的图形绘制方法 gr." + method
                        + "（可用: plotArc/plotCircle/plotCross/plotLine/plotPoint/plotRegion/plotString/plotShape）"));
            }
        }
        Matcher req = REQUIRE_CALL.matcher(text);
        while (req.find()) {
            String mod = req.group(1);
            if (!BUILTIN_MODULES.contains(mod) && !mod.startsWith("./") && !mod.startsWith("/")
                    && !mod.startsWith("ftp://") && !mod.endsWith(".js")) {
                out.add(position(text, req.start(1), req.end(1), 2,
                        "require 的目标模块 \"" + mod + "\" 无法解析（内建模块: fs、cognex）"));
            }
        }
        return out;
    }

    /** 提取 offset 处的标识符（含 gr.xxx 成员访问的完整形式）。 */
    public static String wordAt(String text, int offset) {
        if (offset > text.length()) return "";
        int start = offset;
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;
        // 向前吸收成员访问前缀（gr.xxx / module.exports / this.foo）
        while (start > 1 && text.charAt(start - 1) == '.'
                && isWordChar(text.charAt(start - 2))) {
            int p = start - 2;
            while (p > 0 && isWordChar(text.charAt(p - 1))) p--;
            start = p;
        }
        return text.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 若 offset 处于某个调用 Name(...) 的参数列表内，返回调用的名称与第几个参数；
     * 否则返回 null。用于 signatureHelp。
     */
    public static CallContext callContext(String text, int offset) {
        if (offset > text.length()) return null;
        String head = text.substring(0, offset);
        int depth = 0;
        int commaCount = 0;
        for (int i = head.length() - 1; i >= 0; i--) {
            char c = head.charAt(i);
            if (c == ')' || c == '}' || c == ']') depth++;
            else if (c == '(' || c == '{' || c == '[') {
                if (depth == 0) {
                    if (c == '(') {
                        // 找调用名
                        int j = i - 1;
                        while (j >= 0 && (Character.isWhitespace(head.charAt(j)))) j--;
                        int end = j + 1;
                        while (j >= 0 && isWordChar(head.charAt(j))) j--;
                        if (j >= 0 && head.charAt(j) == '.') {
                            int memberEnd = end;
                            while (j >= 0 && (isWordChar(head.charAt(j)) || head.charAt(j) == '.')) j--;
                            return new CallContext(head.substring(j + 1, memberEnd), commaCount);
                        }
                        if (end > j + 1) return new CallContext(head.substring(j + 1, end), commaCount);
                        return null;
                    }
                    return null; // 停在 {} [] 内，不是函数参数
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                commaCount++;
            }
        }
        return null;
    }

    public record CallContext(String name, int activeParameter) {}

    private static Diagnostic position(String text, int start, int end, int severity, String msg) {
        int line = 0, lineStart = 0;
        for (int i = 0; i < start; i++) {
            if (text.charAt(i) == '\n') { line++; lineStart = i + 1; }
        }
        return new Diagnostic(line, start - lineStart, Math.max(end - lineStart, start - lineStart + 1),
                severity, msg);
    }

    /** 括号配平与未终止字符串/注释检查。 */
    private static void balanceCheck(String text, List<Diagnostic> out) {
        Deque<int[]> stack = new ArrayDeque<>();
        boolean inLine = false, inBlock = false, inStr = false, inTemplate = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (inLine) {
                if (c == '\n') inLine = false;
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') { inBlock = false; i++; }
                continue;
            }
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == quote) inStr = false;
                continue;
            }
            if (inTemplate) {
                if (c == '\\') { i++; continue; }
                if (c == '`') inTemplate = false;
                continue;
            }
            if (c == '/' && next == '/') { inLine = true; i++; continue; }
            if (c == '/' && next == '*') { inBlock = true; i++; continue; }
            if (c == '"' || c == '\'') { inStr = true; quote = c; continue; }
            if (c == '`') { inTemplate = true; continue; }
            switch (c) {
                case '(' -> stack.push(new int[]{i, ')'});
                case '[' -> stack.push(new int[]{i, ']'});
                case '{' -> stack.push(new int[]{i, '}'});
                case ')', ']', '}' -> {
                    if (stack.isEmpty() || stack.peek()[1] != c) {
                        out.add(position(text, i, i + 1, 1, "多余的 '" + c + "'，没有匹配的开启括号"));
                    } else {
                        stack.pop();
                    }
                }
                default -> { }
            }
        }
        if (inStr || inTemplate) {
            out.add(position(text, text.length() - 1, text.length(), 1, "字符串未终止"));
        }
        if (inBlock) {
            out.add(position(text, text.length() - 1, text.length(), 1, "块注释未终止"));
        }
        while (!stack.isEmpty()) {
            int[] open = stack.pop();
            out.add(position(text, open[0], open[0] + 1, 1,
                    "括号 '" + (char) open[1] + "' 未闭合"));
        }
    }
}
