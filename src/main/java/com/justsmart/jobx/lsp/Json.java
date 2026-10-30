package com.justsmart.jobx.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析与序列化（无第三方依赖）。
 * 解析结果为 Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null。
 */
public final class Json {

    private Json() {}

    // ---------- 序列化 ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------- 解析 ----------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.parseValue();
        p.skipWs();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        return (Map<String, Object>) parse(text);
    }

    public static String stringValue(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static List<Object> arrayValue(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'
                    || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield parseNumber();
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON parse error at " + i + ": " + msg);
        }

        private Object parseLiteral(String lit, Object value) {
            if (!s.startsWith(lit, i)) throw error("invalid literal");
            i += lit.length();
            return value;
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') throw error("expected ':'");
                i++;
                map.put(key, parseValue());
                skipWs();
                if (i >= s.length()) throw error("unterminated object");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return map; }
                throw error("expected ',' or '}'");
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (i >= s.length()) throw error("unterminated array");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return list; }
                throw error("expected ',' or ']'");
            }
        }

        private String parseString() {
            if (s.charAt(i) != '"') throw error("expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw error("bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw error("bad escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("unterminated string");
        }

        private Object parseNumber() {
            int start = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
