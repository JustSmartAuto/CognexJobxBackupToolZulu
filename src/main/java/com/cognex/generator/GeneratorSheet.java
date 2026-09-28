package com.cognex.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成器 sheet POJO。对应 sheet JSON：{"$type":"Sheet","cells":[...],"columnWidths":...,...}.
 * Generator sheet POJO; serializes to plain sheet JSON.
 */
public class GeneratorSheet {
    public final String name;
    public final Map<String, GeneratorCell> cells = new LinkedHashMap<>();
    public List<Integer> columnWidths = new ArrayList<>();
    public List<Integer> rowHeights = new ArrayList<>();
    public double coreThreshold = 0.05;
    public String outputs = "";
    public int processingCores = 1;
    public int timeout = 60000;

    public GeneratorSheet(String name) {
        this.name = name;
    }

    /** 序列化为 sheet JSON 对象（明文，未 XOR）/ Serialize to plain sheet JSON object. */
    public JsonObject toSheetJson() {
        JsonObject o = new JsonObject();
        o.addProperty("$type", "Sheet");
        JsonArray cellsArr = new JsonArray();
        for (GeneratorCell c : cells.values()) {
            cellsArr.add(c.toCellArray());
        }
        o.add("cells", cellsArr);

        JsonArray cw = new JsonArray();
        for (int w : columnWidths) cw.add(w);
        o.add("columnWidths", cw);

        o.addProperty("coreThreshold", coreThreshold);
        o.addProperty("outputs", outputs);
        o.addProperty("processingCores", processingCores);

        JsonArray rh = new JsonArray();
        for (int h : rowHeights) rh.add(h);
        o.add("rowHeights", rh);

        o.addProperty("timeout", timeout);
        return o;
    }

    /** sheet JSON 的 UTF-8 字节（用于 .jobx 的 inline Byte[] 嵌入）/ UTF-8 bytes of sheet JSON. */
    public byte[] toSheetBytes() throws UnsupportedEncodingException {
        return toSheetJson().toString().getBytes("UTF-8");
    }

    /**
     * snippet.json（.cxdx 用）的 UTF-8 字节。
     * 顶层格式：{"$type":"CopyBufferObject","range":"A1:Z<n>","cells":[...],"columnWidths":...,...}
     * 其余 sheet 元数据字段（columnWidths/rowHeights/timeout 等）一并写入，便于粘贴时还原。
     */
    public byte[] toSnippetBytes(String range) throws UnsupportedEncodingException {
        JsonObject o = new JsonObject();
        o.addProperty("$type", "CopyBufferObject");
        o.addProperty("range", range);

        JsonArray cellsArr = new JsonArray();
        for (GeneratorCell c : cells.values()) {
            cellsArr.add(c.toCellArray());
        }
        o.add("cells", cellsArr);

        JsonArray cw = new JsonArray();
        for (int w : columnWidths) cw.add(w);
        o.add("columnWidths", cw);

        o.addProperty("coreThreshold", coreThreshold);
        o.addProperty("outputs", outputs);
        o.addProperty("processingCores", processingCores);

        JsonArray rh = new JsonArray();
        for (int h : rowHeights) rh.add(h);
        o.add("rowHeights", rh);

        o.addProperty("timeout", timeout);
        return o.toString().getBytes("UTF-8");
    }
}
