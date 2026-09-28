package com.cognex.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;

/**
 * 生成器单元格 POJO，对应 cell 12 元素数组。
 * Generator cell POJO, maps to the 12-element cell array.
 *
 * 12 元素官方语义（CellJsonConverter）：
 *   [location, expression, condition, value, name, saved,
 *    cellStyle, graphicsStyle, comment, input, output, ipProtected]
 *
 * 已知问题：saved 字段恒为 null。
 * PatMax/Caliper 等训练型工具表达式可写入，但训练状态（saved）无法离线生成，
 * 装入相机后这些 cell 可能无法直接运行（已知问题）。
 */
public class GeneratorCell {
    public String location = "";
    public String expression = "";
    public String condition = "1";  // 默认正常状态 / default state
    public String value = null;     // 运行时值，离线生成留空 / runtime value, null when generating
    public String name = "";
    public String saved = null;      // 训练态；用户要求恒为 null / training state; always null per requirement
    public String cellStyle = "";
    public String graphicsStyle = "";
    public String comment = "";
    public int input = 0;
    public int output = 0;
    public int ipProtected = 0;

    /** 序列化为 12 元素 JSON 数组 / Serialize to 12-element JSON array. */
    public JsonArray toCellArray() {
        JsonArray arr = new JsonArray();
        arr.add(location == null ? "" : location);
        arr.add(expression == null ? "" : expression);
        arr.add(condition == null ? "1" : condition);
        if (value == null) {
            arr.add(JsonNull.INSTANCE);
        } else {
            arr.add(value);
        }
        arr.add(name == null ? "" : name);
        if (saved == null) {
            arr.add(JsonNull.INSTANCE);
        } else {
            arr.add(saved);
        }
        arr.add(cellStyle == null ? "" : cellStyle);
        arr.add(graphicsStyle == null ? "" : graphicsStyle);
        arr.add(comment == null ? "" : comment);
        arr.add(input);
        arr.add(output);
        arr.add(ipProtected);
        return arr;
    }
}
