package com.cognex.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线解析 .jobx / .cxdx 文件 → 提取所有 sheet 的单元格数据。
 * Offline parser for .jobx / .cxdx files; extracts all sheets and cells.
 *
 * 文件格式（详见 JobxBinaryStructure.md）：
 * - .jobx = POSIX TAR 归档（V7 变体，无 ustar magic）
 *   * Job.json：明文 UTF-8，Sheets.<name> 可能 inline base64（Byte[]）或 FileRef 引用 sheets/<hash>
 *   * sheets/<hash>：4 字节循环 XOR 混淆（密钥 0x72 0x9B 0x0F 0x2E），内容为 sheet JSON
 *   * data/<hash>、*.sig、JobValidationSet/ 等：导出 xlsx 时忽略
 * - .cxdx = 同族 TAR 格式，snippet.json 用同 XOR 密钥
 *   结构 {"$type":"CopyBufferObject","range":"A1:Z318","cells":[...],...}
 *
 * cell 数组 12 元素官方语义（CellJsonConverter）：
 *   [location, expression, condition, value, name, saved, cellStyle, graphicsStyle, comment, input, output, ipProtected]
 */
public class JobxParser {

    // XOR 混淆密钥 / XOR obfuscation key (Cognex 官方硬编码常量 DeobfuscateBytes)
    private static final byte[] XOR_KEY = { 0x72, (byte) 0x9B, 0x0F, 0x2E };

    private JobxParser() {
    }

    /**
     * 解析 .jobx 或 .cxdx 文件。
     * Parse a .jobx or .cxdx file.
     */
    public static ParsedJob parse(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".cxdx")) {
            return parseCxdx(file);
        } else if (name.endsWith(".jobx")) {
            return parseJobx(file);
        }
        throw new IllegalArgumentException("仅支持 .jobx 或 .cxdx 文件，当前: " + file.getName());
    }

    // ======================== .jobx 解析 ========================

    private static ParsedJob parseJobx(File file) throws Exception {
        Map<String, byte[]> entries = readTarEntries(file);
        ParsedJob job = new ParsedJob(file);

        // 1. 取 Job.json / extract Job.json
        byte[] jobJsonBytes = entries.get("Job.json");
        if (jobJsonBytes == null) {
            throw new IOException("未在 .jobx 中找到 Job.json 条目");
        }
        JsonObject jobJson = parseJsonUtf8(jobJsonBytes).getAsJsonObject();

        // 元数据 / metadata
        copyMeta(job, jobJson);

        // 2. 处理 Sheets 字段 / process Sheets
        if (jobJson.has("Sheets") && jobJson.get("Sheets").isJsonObject()) {
            JsonObject sheets = jobJson.getAsJsonObject("Sheets");
            for (Map.Entry<String, JsonElement> e : sheets.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                ParsedSheet sh = resolveSheetInlineOrRef(e.getKey(), e.getValue().getAsJsonObject(), entries);
                if (sh != null) job.sheets.add(sh);
            }
        } else {
            // 旧格式：顶层 Acquisition / Inspection 直接是 sheet / Legacy: top-level sheet
            for (Map.Entry<String, JsonElement> e : jobJson.entrySet()) {
                String key = e.getKey();
                if ("Acquisition".equals(key) || "Inspection".equals(key)) {
                    if (e.getValue().isJsonObject()) {
                        ParsedSheet sh = resolveSheetInlineOrRef(key, e.getValue().getAsJsonObject(), entries);
                        if (sh != null) job.sheets.add(sh);
                    }
                }
            }
        }
        return job;
    }

    private static ParsedSheet resolveSheetInlineOrRef(String name, JsonObject ref,
                                                      Map<String, byte[]> entries) throws Exception {
        String type = jsonType(ref);
        byte[] sheetBytes;
        if ("FileRef".equals(type)) {
            // 独立 sheets/<hash> 对象，XOR 混淆 / External sheets/<hash> entry, XOR-obfuscated
            String id = ref.has("id") ? ref.get("id").getAsString() : null;
            if (id == null) return null;
            byte[] enc = entries.get(id);
            if (enc == null) {
                // 找不到加密块，跳过 / Cannot find XOR blob, skip silently
                return null;
            }
            sheetBytes = deobfuscate(enc);
        } else if ("Byte[]".equals(type)) {
            // inline base64：明文 sheet JSON / inline base64: plain sheet JSON
            String b64 = ref.has("base64") ? ref.get("base64").getAsString() : "";
            sheetBytes = java.util.Base64.getDecoder().decode(b64);
        } else {
            // 可能直接是 sheet 对象 / Maybe inline sheet object
            sheetBytes = ref.toString().getBytes("UTF-8");
        }
        JsonObject sheetJson = parseJsonUtf8(sheetBytes).getAsJsonObject();
        return parseSheet(name, sheetJson);
    }

    // ======================== .cxdx 解析 ========================

    private static ParsedJob parseCxdx(File file) throws Exception {
        Map<String, byte[]> entries = readTarEntries(file);
        ParsedJob job = new ParsedJob(file);

        byte[] snipBytes = entries.get("snippet.json");
        if (snipBytes == null) {
            throw new IOException("未在 .cxdx 中找到 snippet.json 条目");
        }
        byte[] plain = deobfuscate(snipBytes);
        JsonObject root = parseJsonUtf8(plain).getAsJsonObject();

        // snippet.json 顶层含 cells 数组 / snippet top-level has cells array
        ParsedSheet sh = parseSheet("snippet", root);
        job.sheets.add(sh);

        if (root.has("range") && root.get("range").isJsonPrimitive()) {
            job.meta.put("range", root.get("range").getAsString());
        }
        return job;
    }

    // ======================== Sheet 解析 ========================

    private static ParsedSheet parseSheet(String name, JsonObject sheetJson) {
        ParsedSheet sheet = new ParsedSheet(name);
        if (!sheetJson.has("cells") || !sheetJson.get("cells").isJsonArray()) return sheet;
        JsonArray cells = sheetJson.getAsJsonArray("cells");
        for (JsonElement e : cells) {
            ParsedCell cell = parseCell(e);
            if (cell != null) sheet.cells.add(cell);
        }
        return sheet;
    }

    private static ParsedCell parseCell(JsonElement e) {
        if (e.isJsonArray()) {
            // 数组形式（默认）/ Array form (default)
            JsonArray arr = e.getAsJsonArray();
            ParsedCell c = new ParsedCell();
            c.location = jsonStr(arr, 0);
            c.expression = jsonStr(arr, 1);
            // index 2 condition：跳过
            c.value = jsonAny(arr, 3);
            c.name = jsonStr(arr, 4);
            // 5 saved：FileRef/Byte[]，导出 xlsx 无意义，跳过
            // 6 cellStyle, 7 graphicsStyle：跳过
            c.comment = jsonStr(arr, 8);
            // 9/10/11 input/output/ipProtected：跳过
            return c;
        }
        if (e.isJsonObject()) {
            // 对象形式 / Object form
            JsonObject o = e.getAsJsonObject();
            ParsedCell c = new ParsedCell();
            c.location = jsonStr(o, "location");
            c.expression = jsonStr(o, "expression");
            c.value = jsonAny(o, "value");
            c.name = jsonStr(o, "name");
            c.comment = jsonStr(o, "comment");
            return c;
        }
        return null;
    }

    // ======================== TAR 读取 ========================

    /**
     * 读取 TAR 所有条目。
     * 仅保留 Job.json / sheets/* / snippet.json，跳过 data/* 与 *.sig（避免大块占用内存）。
     *
     * Read all TAR entries. Only Job.json / sheets/* / snippet.json are kept;
     * data/* and *.sig are skipped to avoid holding large blobs in memory.
     */
    private static Map<String, byte[]> readTarEntries(File file) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[512];
            while (true) {
                int n = readFully(fis, header);
                if (n < 512) break;
                if (isAllZero(header)) break;  // 结束块 / end-of-archive block
                String name = readCString(header, 0, 100);
                if (name.isEmpty()) break;
                long size = parseOctal(header, 124, 12);

                // 跳过 data/* 与 *.sig（避免大对象占用内存） / Skip data/* and *.sig
                if (name.startsWith("data/") || name.endsWith(".sig")) {
                    long aligned = (size + 511) & ~511L;
                    skipFully(fis, aligned);
                    continue;
                }

                int contentLen = (int) size;
                byte[] content = new byte[contentLen];
                if (contentLen > 0) {
                    int got = readFully(fis, content);
                    if (got != contentLen) break;
                }
                long pad = (512 - (size % 512)) % 512;
                if (pad > 0) skipFully(fis, pad);
                map.put(name, content);
            }
        }
        return map;
    }

    // ======================== 工具 / utility ========================

    private static byte[] deobfuscate(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ XOR_KEY[i & 3]);
        }
        return out;
    }

    private static JsonElement parseJsonUtf8(byte[] data) throws UnsupportedEncodingException {
        return JsonParser.parseString(new String(data, "UTF-8"));
    }

    private static String jsonType(JsonObject o) {
        if (o.has("$type") && o.get("$type").isJsonPrimitive()) {
            return o.get("$type").getAsString();
        }
        return "";
    }

    private static String jsonStr(JsonArray a, int i) {
        if (i >= a.size() || a.get(i).isJsonNull()) return "";
        JsonElement e = a.get(i);
        if (e.isJsonPrimitive()) return e.getAsString();
        return e.toString();
    }

    private static String jsonStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return "";
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive()) return e.getAsString();
        return e.toString();
    }

    private static String jsonAny(JsonArray a, int i) {
        if (i >= a.size() || a.get(i).isJsonNull()) return "";
        return a.get(i).toString();
    }

    private static String jsonAny(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).toString();
    }

    private static void copyMeta(ParsedJob job, JsonObject jobJson) {
        if (jobJson.has("JobVersion") && jobJson.get("JobVersion").isJsonPrimitive()) {
            job.meta.put("JobVersion", jobJson.get("JobVersion").getAsString());
        }
        if (jobJson.has("Metadata") && jobJson.get("Metadata").isJsonObject()) {
            JsonObject md = jobJson.getAsJsonObject("Metadata");
            for (Map.Entry<String, JsonElement> e : md.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    job.meta.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
    }

    // -------- TAR 头工具 / TAR header utilities --------

    private static String readCString(byte[] b, int off, int len) {
        int end = off;
        int max = Math.min(off + len, b.length);
        while (end < max && b[end] != 0) end++;
        try {
            return new String(b, off, end - off, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(b, off, end - off);
        }
    }

    private static long parseOctal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') break;
            if (c < '0' || c > '7') continue;
            v = (v << 3) | (c - '0');
        }
        return v;
    }

    private static boolean isAllZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) return total;
            total += n;
        }
        return total;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long s = in.skip(left);
            if (s <= 0) {
                int r = in.read();
                if (r < 0) break;
                left--;
            } else {
                left -= s;
            }
        }
    }

    // ======================== POJO ========================

    /** 解析结果 / Parsed result. */
    public static class ParsedJob {
        public final File sourceFile;
        public final Map<String, String> meta = new HashMap<>();
        public final List<ParsedSheet> sheets = new ArrayList<>();

        public ParsedJob(File sourceFile) {
            this.sourceFile = sourceFile;
        }
    }

    public static class ParsedSheet {
        public final String name;
        public final List<ParsedCell> cells = new ArrayList<>();

        public ParsedSheet(String name) {
            this.name = name;
        }
    }

    public static class ParsedCell {
        public String location = "";
        public String expression = "";
        public String value = "";
        public String name = "";
        public String comment = "";
    }
}
