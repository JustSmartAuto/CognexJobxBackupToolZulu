package com.cognex.i18n;

import com.cognex.generator.JobxWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Jobx / cxdx 中英翻译工具（字节级手术，供 agent skill 调用）。
 *
 * 两阶段流程：
 *   1) extract：扫描所有 sheet 的单元格，抽取含汉字的字符串（name / comment / value /
 *      表达式双引号字面量 / Job Metadata 字符串值），输出待翻译 JSON 映射文件；
 *   2) apply：读取填好 en 的映射，回写为新的 *_en_<时间戳>.jobx / .cxdx。
 *
 * 训练态保证（用户硬性要求）：
 *   - cell[5] saved 字段从不读取/修改，原样保留；
 *   - data/* 训练数据块及其他所有 TAR 条目（JobValidationSet/ 等）逐字节复制；
 *   - 仅替换上述四个文本字段中的中文，表达式结构、单元格位置、样式、条件等全部不动；
 *   - AcqSettings / JobSettings / JobVersion 等 Job.json 其余部分不触碰；
 *   - 重写后按原算法重新计算 .sig（HMAC-SHA256）。
 *
 * 存储约定（与 JobxParser 一致）：
 *   - inline Byte[] sheet：base64 承载【明文】 sheet JSON（无 XOR），回写时同步更新 sz；
 *   - FileRef sheets/<hash>：条目内容为 XOR 密文；
 *   - .cxdx snippet.json：XOR 密文；其 .sig 签 XOR 后密文；
 *   - .jobx Job.json.sig：签 Job.json 明文字节。
 */
public class JobxI18n {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 汉字检测（CJK 扩展A + 基本区 + 兼容象形）/ Han-character detection. */
    private static final Pattern HAN = Pattern.compile("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]");
    private static final long MAX_BLOB = 16L * 1024 * 1024;

    // 单元格 12 元素索引 / cell 12-element array indices
    private static final int IDX_EXPRESSION = 1;
    private static final int IDX_VALUE = 3;
    private static final int IDX_NAME = 4;
    private static final int IDX_SAVED = 5;
    private static final int IDX_COMMENT = 8;

    private JobxI18n() {
    }

    // ======================== 阶段 1：抽取 / extract ========================

    /** 抽取结果统计 / Extract statistics. */
    public static class ExtractStats {
        public int cells;
        public int savedCells;
        public int dataEntries;
        public int uniqueStrings;
    }

    /**
     * 扫描源文件，把所有含汉字的可翻译字符串写入 mapFile（en 留空待人工/LLM 填）。
     * Extract translatable Chinese strings into a JSON map file (en left empty).
     */
    public static ExtractStats extract(File src, File mapFile) throws IOException {
        List<TarEntry> entries = readTarOrdered(src);
        boolean cxdx = src.getName().toLowerCase().endsWith(".cxdx");

        // zh 原文 → 条目（保留首次出现顺序）/ zh text -> entry, first-seen order
        LinkedHashMap<String, JsonObject> stringMap = new LinkedHashMap<String, JsonObject>();
        ExtractStats stats = new ExtractStats();
        stats.dataEntries = countDataEntries(entries);

        if (cxdx) {
            TarEntry te = mustFind(entries, "snippet.json");
            JsonObject snippet = parseJson(JobxWriter.xor(te.bytes)).getAsJsonObject();
            collectFromSheet(snippet, "snippet", stringMap, stats);
        } else {
            byte[] jobBytes = mustFind(entries, "Job.json").bytes;
            JsonObject job = parseJson(jobBytes).getAsJsonObject();
            if (job.has("Sheets") && job.get("Sheets").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : job.getAsJsonObject("Sheets").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject ref = e.getValue().getAsJsonObject();
                    byte[] sheetBytes = resolvePlainSheetBytes(ref, entries);
                    if (sheetBytes == null) continue;
                    collectFromSheet(parseJson(sheetBytes).getAsJsonObject(), e.getKey(), stringMap, stats);
                }
            }
            collectFromMetadata(job, stringMap, stats);
        }

        // 组装映射文件 / build map file
        JsonObject root = new JsonObject();
        root.addProperty("source", src.getAbsolutePath());
        root.addProperty("format", cxdx ? "cxdx" : "jobx");
        root.addProperty("note", "在每条 strings[].en 填入英文译文；留空表示保留中文不翻译。"
                + "必须保留占位符（%f/%1.2f/\\n 等）、数字与单位；表达式字面量不要改动引号外的任何内容。");
        JsonArray arr = new JsonArray();
        for (JsonObject o : stringMap.values()) arr.add(o);
        root.add("strings", arr);

        File parent = mapFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        FileOutputStream fos = new FileOutputStream(mapFile);
        try {
            fos.write(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
        stats.uniqueStrings = stringMap.size();
        return stats;
    }

    // ======================== 阶段 2：回写 / apply ========================

    /** 回写结果统计 / Apply statistics. */
    public static class ApplyStats {
        public File outFile;
        public int cells;
        public int savedCells;          // 带训练状态的单元格数（原样保留）
        public int dataEntries;         // 原样复制的 data/* 条目数
        public int replacedNames;
        public int replacedComments;
        public int replacedValues;
        public int replacedExprLiterals;
        public int replacedMetadata;
        public int usedMapEntries;
        public final List<String> remainingCjk = new ArrayList<String>();  // 仍含汉字的串（去重，上限 50）
    }

    /**
     * 按映射文件把中文回写为英文，生成新文件；训练态与 data/* 逐字节保留，重算签名。
     * Apply the zh→en map and emit a new *_en_<timestamp> file; saved/data byte-preserved, re-signed.
     *
     * @param outDir  输出目录（null = 源文件同目录）
     * @param baseName 输出文件名前缀（null = 源 stem + "_en"）
     * @param noSig   true = 不写 .sig
     */
    public static ApplyStats apply(File src, File mapFile, File outDir, String baseName, boolean noSig)
            throws IOException {
        JsonObject mapRoot = parseJson(readFile(mapFile)).getAsJsonObject();
        LinkedHashMap<String, String> zh2en = new LinkedHashMap<String, String>();
        if (mapRoot.has("strings") && mapRoot.get("strings").isJsonArray()) {
            for (JsonElement e : mapRoot.getAsJsonArray("strings")) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("zh")) continue;
                String zh = o.get("zh").getAsString();
                String en = o.has("en") && !o.get("en").isJsonNull() ? o.get("en").getAsString() : "";
                if (!en.isEmpty()) zh2en.put(zh, en);
            }
        }

        boolean cxdx = src.getName().toLowerCase().endsWith(".cxdx");
        List<TarEntry> entries = readTarOrdered(src);
        ApplyStats stats = new ApplyStats();
        stats.dataEntries = countDataEntries(entries);

        byte[] newJobBytes = null;
        if (cxdx) {
            TarEntry te = mustFind(entries, "snippet.json");
            JsonObject snippet = parseJson(JobxWriter.xor(te.bytes)).getAsJsonObject();
            translateSheet(snippet, "snippet", zh2en, stats);
            te.bytes = JobxWriter.xor(GSON.toJson(snippet).getBytes(StandardCharsets.UTF_8));
        } else {
            TarEntry jobEntry = mustFind(entries, "Job.json");
            JsonObject job = parseJson(jobEntry.bytes).getAsJsonObject();
            if (job.has("Sheets") && job.get("Sheets").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : job.getAsJsonObject("Sheets").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject ref = e.getValue().getAsJsonObject();
                    String type = ref.has("$type") && ref.get("$type").isJsonPrimitive()
                            ? ref.get("$type").getAsString() : "";
                    if ("Byte[]".equals(type)) {
                        // inline base64 = 明文 sheet JSON / inline base64 carries PLAIN sheet JSON
                        byte[] plain = Base64.getDecoder().decode(
                                ref.has("base64") ? ref.get("base64").getAsString() : "");
                        JsonObject sheet = parseJson(plain).getAsJsonObject();
                        translateSheet(sheet, e.getKey(), zh2en, stats);
                        byte[] out = GSON.toJson(sheet).getBytes(StandardCharsets.UTF_8);
                        ref.addProperty("sz", out.length);
                        ref.addProperty("base64", Base64.getEncoder().encodeToString(out));
                    } else if ("FileRef".equals(type)) {
                        // sheets/<hash> = XOR 密文 / external sheet entry is XOR-obfuscated
                        String id = ref.has("id") ? ref.get("id").getAsString() : null;
                        if (id == null) continue;
                        TarEntry te = findEntry(entries, id);
                        if (te == null) continue;
                        JsonObject sheet = parseJson(JobxWriter.xor(te.bytes)).getAsJsonObject();
                        translateSheet(sheet, e.getKey(), zh2en, stats);
                        te.bytes = JobxWriter.xor(GSON.toJson(sheet).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            translateMetadata(job, zh2en, stats);
            newJobBytes = GSON.toJson(job).getBytes(StandardCharsets.UTF_8);
            jobEntry.bytes = newJobBytes;
        }

        // 剩余汉字扫描 / post-run remaining-CJK scan
        if (cxdx) {
            JsonObject snippet = parseJson(JobxWriter.xor(mustFind(entries, "snippet.json").bytes)).getAsJsonObject();
            collectRemainingCjk(snippet, stats);
        } else {
            JsonObject job = parseJson(mustFind(entries, "Job.json").bytes).getAsJsonObject();
            if (job.has("Sheets") && job.get("Sheets").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : job.getAsJsonObject("Sheets").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    byte[] plain = resolvePlainSheetBytes(e.getValue().getAsJsonObject(), entries);
                    if (plain == null) continue;
                    collectRemainingCjk(parseJson(plain).getAsJsonObject(), stats);
                }
            }
        }

        // 输出文件名 / output name：{stem}_en_<yyyyMMdd_HHmmss>.<原扩展名>
        String stem = stripExt(src.getName());
        String base = baseName != null && !baseName.isEmpty() ? baseName : stem + "_en";
        String ext = cxdx ? ".cxdx" : ".jobx";
        File dir = outDir != null ? outDir : src.getParentFile();
        if (dir == null) dir = new File(".");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建输出目录: " + dir);
        File outFile = new File(dir, base + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ext);

        FileOutputStream fos = new FileOutputStream(outFile);
        try {
            // 原条目顺序写出（*.sig 在读取时已丢弃），data/* 等字节原样 / preserve entry order; sigs dropped
            for (TarEntry te : entries) {
                JobxWriter.writeTarEntry(fos, te.name, te.bytes);
            }
            if (!noSig) {
                if (cxdx) {
                    byte[] cipher = mustFind(entries, "snippet.json").bytes;
                    JobxWriter.writeTarEntry(fos, "snippet.json.sig", JobxWriter.computeSig(cipher));
                } else {
                    JobxWriter.writeTarEntry(fos, "Job.json.sig", JobxWriter.computeSig(newJobBytes));
                }
            }
            JobxWriter.writeEndOfArchive(fos);
        } finally {
            fos.close();
        }
        stats.outFile = outFile;
        stats.usedMapEntries = zh2en.size();
        return stats;
    }

    // ======================== 抽取扫描 / collection ========================

    /** 扫描一个 sheet（或 snippet 根对象）的 cells，收集中文串。 */
    private static void collectFromSheet(JsonObject sheet, String sheetName,
                                         LinkedHashMap<String, JsonObject> map, ExtractStats stats) {
        if (!sheet.has("cells") || !sheet.get("cells").isJsonArray()) return;
        for (JsonElement ce : sheet.getAsJsonArray("cells")) {
            if (!ce.isJsonArray()) continue;
            JsonArray c = ce.getAsJsonArray();
            stats.cells++;
            if (hasSaved(c)) stats.savedCells++;
            String loc = strAt(c, 0);

            collectWholeField(strAt(c, IDX_NAME), sheetName, loc, "name", map);
            collectWholeField(strAt(c, IDX_COMMENT), sheetName, loc, "comment", map);
            // value 仅在为字符串字面量时收集（数字/布尔/Image 字典不译）
            if (c.size() > IDX_VALUE && c.get(IDX_VALUE).isJsonPrimitive()
                    && c.get(IDX_VALUE).getAsJsonPrimitive().isString()) {
                collectWholeField(c.get(IDX_VALUE).getAsString(), sheetName, loc, "value", map);
            }
            // 表达式：只收双引号字面量内容（Cognex 用 "" 转义内嵌引号）
            String expr = strAt(c, IDX_EXPRESSION);
            for (int[] r : findExprLiterals(expr)) {
                collectWholeField(expr.substring(r[0], r[1]), sheetName, loc, "expr", map);
            }
            // 单引号前缀文本常量（In-Sight 与 Excel 同构：'文本 → 整格静态文本），后缀整体可译
            if (expr.startsWith("'") && expr.length() > 1) {
                collectWholeField(expr.substring(1), sheetName, loc, "expr", map);
            }
        }
    }

    private static void collectFromMetadata(JsonObject job,
                                            LinkedHashMap<String, JsonObject> map, ExtractStats stats) {
        if (!job.has("Metadata") || !job.get("Metadata").isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : job.getAsJsonObject("Metadata").entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                collectWholeField(v.getAsString(), "(Metadata)", e.getKey(), "metadata", map);
            }
        }
    }

    private static void collectWholeField(String text, String sheet, String location, String field,
                                          LinkedHashMap<String, JsonObject> map) {
        if (text == null || text.isEmpty() || !HAN.matcher(text).find()) return;
        JsonObject entry = map.get(text);
        if (entry == null) {
            entry = new JsonObject();
            entry.addProperty("zh", text);
            entry.addProperty("en", "");
            entry.addProperty("occurrences", 0);
            entry.add("contexts", new JsonArray());
            map.put(text, entry);
        }
        entry.addProperty("occurrences", entry.get("occurrences").getAsInt() + 1);
        JsonArray ctxs = entry.getAsJsonArray("contexts");
        if (ctxs.size() < 10) {  // 每个串最多记录 10 个出处，避免映射文件过大
            JsonObject ctx = new JsonObject();
            ctx.addProperty("sheet", sheet);
            ctx.addProperty("location", location);
            ctx.addProperty("field", field);
            ctxs.add(ctx);
        }
    }

    // ======================== 回写翻译 / translation ========================

    private static void translateSheet(JsonObject sheet, String sheetName,
                                       LinkedHashMap<String, String> zh2en, ApplyStats stats) {
        if (!sheet.has("cells") || !sheet.get("cells").isJsonArray()) return;
        for (JsonElement ce : sheet.getAsJsonArray("cells")) {
            if (!ce.isJsonArray()) continue;
            JsonArray c = ce.getAsJsonArray();
            stats.cells++;
            if (hasSaved(c)) stats.savedCells++;
            // saved（索引 5）刻意不碰 —— 训练态原封不动 / saved (index 5) intentionally untouched

            stats.replacedNames += replaceWholeField(c, IDX_NAME, zh2en);
            stats.replacedComments += replaceWholeField(c, IDX_COMMENT, zh2en);
            if (c.size() > IDX_VALUE && c.get(IDX_VALUE).isJsonPrimitive()
                    && c.get(IDX_VALUE).getAsJsonPrimitive().isString()) {
                String en = zh2en.get(c.get(IDX_VALUE).getAsString());
                if (en != null) { c.set(IDX_VALUE, new JsonPrimitive(en)); stats.replacedValues++; }
            }
            if (c.size() > IDX_EXPRESSION && c.get(IDX_EXPRESSION).isJsonPrimitive()
                    && c.get(IDX_EXPRESSION).getAsJsonPrimitive().isString()) {
                String expr = c.get(IDX_EXPRESSION).getAsString();
                String rebuilt = replaceExprLiterals(expr, zh2en);
                // 单引号前缀文本常量：后缀整体命中映射则替换（双引号字面量替换优先）
                if (rebuilt == null && expr.startsWith("'") && expr.length() > 1) {
                    String en = zh2en.get(expr.substring(1));
                    if (en != null) rebuilt = "'" + en;
                }
                if (rebuilt != null) {
                    c.set(IDX_EXPRESSION, new JsonPrimitive(rebuilt));
                    stats.replacedExprLiterals++;
                }
            }
        }
    }

    private static void translateMetadata(JsonObject job,
                                          LinkedHashMap<String, String> zh2en, ApplyStats stats) {
        if (!job.has("Metadata") || !job.get("Metadata").isJsonObject()) return;
        JsonObject md = job.getAsJsonObject("Metadata");
        for (Map.Entry<String, JsonElement> e : md.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String en = zh2en.get(v.getAsString());
                if (en != null) { e.setValue(new JsonPrimitive(en)); stats.replacedMetadata++; }
            }
        }
    }

    /** 整字段全等替换；返回替换次数（0 或 1）。 */
    private static int replaceWholeField(JsonArray c, int idx, LinkedHashMap<String, String> zh2en) {
        if (c.size() <= idx) return 0;
        JsonElement el = c.get(idx);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return 0;
        String en = zh2en.get(el.getAsString());
        if (en == null) return 0;
        c.set(idx, new JsonPrimitive(en));
        return 1;
    }

    /**
     * 替换表达式中双引号字面量的内容；返回新表达式，无替换返回 null。
     * 字面量内 "" 是内嵌引号；译文里的 " 按 "" 重新转义，引号外的内容逐字符保留。
     */
    private static String replaceExprLiterals(String expr, LinkedHashMap<String, String> zh2en) {
        List<int[]> ranges = findExprLiterals(expr);
        boolean changed = false;
        StringBuilder sb = new StringBuilder(expr);
        // 从后往前替换，保持偏移有效 / replace back-to-front to keep offsets valid
        for (int k = ranges.size() - 1; k >= 0; k--) {
            int[] r = ranges.get(k);
            String zh = sb.substring(r[0], r[1]);
            String en = zh2en.get(zh);
            if (en == null) continue;
            String esc = en.replace("\"", "\"\"");
            sb.replace(r[0], r[1], esc);
            changed = true;
        }
        return changed ? sb.toString() : null;
    }

    /**
     * 扫描表达式，返回所有双引号字符串字面量【内容】区间 [start,end)。
     * Cognex 表达式用 "" 表示字面量内嵌的一个引号。
     */
    private static List<int[]> findExprLiterals(String s) {
        List<int[]> out = new ArrayList<int[]>();
        if (s == null || s.isEmpty()) return out;
        int i = 0;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) != '"') { i++; continue; }
            int j = i + 1;
            boolean closed = false;
            while (j < n) {
                if (s.charAt(j) == '"') {
                    if (j + 1 < n && s.charAt(j + 1) == '"') { j += 2; continue; }
                    closed = true;
                    break;
                }
                j++;
            }
            if (closed) {
                out.add(new int[]{ i + 1, j });
                i = j + 1;
            } else {
                break;  // 引号未闭合（畸形表达式），不再继续扫描
            }
        }
        return out;
    }

    // ======================== 剩余汉字扫描 / remaining CJK ========================

    private static void collectRemainingCjk(JsonObject sheet, ApplyStats stats) {
        if (!sheet.has("cells") || !sheet.get("cells").isJsonArray()) return;
        LinkedHashMap<String, Boolean> uniq = new LinkedHashMap<String, Boolean>();
        for (JsonElement ce : sheet.getAsJsonArray("cells")) {
            if (!ce.isJsonArray()) continue;
            JsonArray c = ce.getAsJsonArray();
            addIfHan(uniq, strAt(c, IDX_NAME));
            addIfHan(uniq, strAt(c, IDX_COMMENT));
            if (c.size() > IDX_VALUE && c.get(IDX_VALUE).isJsonPrimitive()
                    && c.get(IDX_VALUE).getAsJsonPrimitive().isString()) {
                addIfHan(uniq, c.get(IDX_VALUE).getAsString());
            }
            String expr = strAt(c, IDX_EXPRESSION);
            for (int[] r : findExprLiterals(expr)) {
                addIfHan(uniq, expr.substring(r[0], r[1]));
            }
            if (expr.startsWith("'") && expr.length() > 1) addIfHan(uniq, expr.substring(1));
        }
        for (String s : uniq.keySet()) {
            if (stats.remainingCjk.size() >= 50) break;
            stats.remainingCjk.add(s);
        }
    }

    private static void addIfHan(LinkedHashMap<String, Boolean> uniq, String s) {
        if (s != null && !s.isEmpty() && HAN.matcher(s).find()) uniq.put(s, Boolean.TRUE);
    }

    // ======================== TAR / JSON 工具 ========================

    private static class TarEntry {
        final String name;
        byte[] bytes;
        TarEntry(String name, byte[] bytes) { this.name = name; this.bytes = bytes; }
    }

    /** 顺序读取 TAR 全部条目（丢弃 *.sig；保留 data/* 等以便逐字节回写）。 */
    private static List<TarEntry> readTarOrdered(File file) throws IOException {
        List<TarEntry> list = new ArrayList<TarEntry>();
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] header = new byte[512];
            while (true) {
                int got = readFully(fis, header);
                if (got < 512) break;
                if (isAllZero(header)) break;
                String name = readCString(header, 0, 100);
                if (name.isEmpty()) break;
                long size = parseOctal(header, 124, 12);
                long aligned = (size + 511) & ~511L;
                if (size > MAX_BLOB) {
                    throw new IOException("TAR 条目超过 16MB 上限，无法安全回写: " + name + " (" + size + " B)");
                }
                byte[] content = new byte[(int) size];
                if (size > 0) {
                    int r = readFully(fis, content);
                    if (r != (int) size) throw new IOException("TAR 条目内容不完整: " + name);
                }
                long pad = aligned - size;
                if (pad > 0) skipFully(fis, pad);
                if (!name.endsWith(".sig")) {
                    list.add(new TarEntry(name, content));  // 旧签名一律丢弃，最后重算
                }
            }
        } finally {
            fis.close();
        }
        return list;
    }

    private static byte[] resolvePlainSheetBytes(JsonObject ref, List<TarEntry> entries) {
        String type = ref.has("$type") && ref.get("$type").isJsonPrimitive()
                ? ref.get("$type").getAsString() : "";
        try {
            if ("Byte[]".equals(type)) {
                return Base64.getDecoder().decode(ref.has("base64") ? ref.get("base64").getAsString() : "");
            }
            if ("FileRef".equals(type)) {
                String id = ref.has("id") ? ref.get("id").getAsString() : null;
                TarEntry te = id == null ? null : findEntry(entries, id);
                return te == null ? null : JobxWriter.xor(te.bytes);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static boolean hasSaved(JsonArray c) {
        return c.size() > IDX_SAVED && c.get(IDX_SAVED) != null && !c.get(IDX_SAVED).isJsonNull();
    }

    private static int countDataEntries(List<TarEntry> entries) {
        int n = 0;
        for (TarEntry te : entries) if (te.name.startsWith("data/")) n++;
        return n;
    }

    private static TarEntry findEntry(List<TarEntry> entries, String name) {
        for (TarEntry te : entries) if (te.name.equals(name)) return te;
        return null;
    }

    private static TarEntry mustFind(List<TarEntry> entries, String name) throws IOException {
        TarEntry te = findEntry(entries, name);
        if (te == null) throw new IOException("归档中缺少条目: " + name);
        return te;
    }

    private static String strAt(JsonArray a, int i) {
        if (a.size() <= i || a.get(i).isJsonNull()) return "";
        JsonElement e = a.get(i);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static JsonElement parseJson(byte[] data) {
        return new JsonParser().parse(new String(data, StandardCharsets.UTF_8));
    }

    private static byte[] readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return b;
        } finally {
            in.close();
        }
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String readCString(byte[] b, int off, int len) {
        int end = off;
        int max = Math.min(off + len, b.length);
        while (end < max && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
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
                if (in.read() < 0) break;
                left--;
            } else {
                left -= s;
            }
        }
    }
}
