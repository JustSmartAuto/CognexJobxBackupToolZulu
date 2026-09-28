package com.cognex.generator;

import cn.net.zhijian.quickjs.JSArray;
import cn.net.zhijian.quickjs.JSObject;
import cn.net.zhijian.quickjs.QuickJSContext;

import java.io.File;
import java.util.Map;

/**
 * 生成器 JS API：注入全局 `jobx` 对象，供脚本构建 cell/sheet/job 后输出为 .jobx / .cxdx / .xlsx。
 *
 * JS 全局方法：
 *   jobx.sheet(name)                      切换/创建 sheet，返回确认字符串
 *   jobx.sheets()                         返回所有 sheet 名数组
 *   jobx.setCell(loc, propsOrExpr)        写单元格；2-arg 字符串=表达式，对象=字段
 *   jobx.getCell(loc)                     返回单元格对象（{location,expression,value,name,comment,cellStyle}）
 *   jobx.getCells()                       返回当前 sheet 全部单元格对象数组
 *   jobx.meta(obj)                        设置 Job 元数据（JobVersion/JobType/CameraType/FirmwareVersion）
 *   jobx.sheetMeta(name, obj)             设置 sheet 元数据（timeout/coreThreshold/processingCores/outputs/columnWidths/rowHeights）
 *   jobx.load(file)                       从已有 .jobx/.cxdx 加载为模板
 *   jobx.output({format,outDir,baseName,noSig})  显式设置输出参数；不调用则脚本结束自动输出 format="all"
 *   jobx.log(msg)                         等同 console.log
 *
 * 单元格 12 元素顺序见 GeneratorCell；saved 字段恒 null（PatMax/Caliper 训练型工具表达式可写但训练态无法离线生成）。
 */
public class GeneratorJsApi {

    private final QuickJSContext ctx;
    private final GeneratorJob job;
    private GeneratorSheet currentSheet;
    private boolean outputCalled = false;
    private OutputConfig outputConfig = new OutputConfig();

    public GeneratorJsApi(QuickJSContext ctx, GeneratorJob job) {
        this.ctx = ctx;
        this.job = job;
        if (!job.sheets.isEmpty()) {
            currentSheet = job.sheets.values().iterator().next();
        }
    }

    /** 输出参数 / Output config (set by JS via jobx.output()). */
    public static class OutputConfig {
        public String format = "all";  // jobx | cxdx | xlsx | all
        public String outDir = null;   // null = 使用默认（GUI: jar dir/generated/，CLI: 脚本父目录）
        public String baseName = null; // null = 使用默认（GUI: generated，CLI: 脚本 stem）
        public boolean noSig = false;
    }

    public GeneratorJob getJob() { return job; }
    public GeneratorSheet getCurrentSheet() { return currentSheet; }
    public boolean wasOutputCalled() { return outputCalled; }
    public OutputConfig getOutputConfig() { return outputConfig; }

    /** 注册到 JS 全局对象 / Register as global `jobx`. */
    public void register() {
        JSObject api = ctx.createJSObject();
        api.setProperty("sheet", (cn.net.zhijian.quickjs.JSCallFunction) args -> sheet(args[0]));
        api.setProperty("sheets", (cn.net.zhijian.quickjs.JSCallFunction) args -> sheets());
        api.setProperty("setCell", (cn.net.zhijian.quickjs.JSCallFunction) args ->
                args.length > 1 ? setCell(args[0], args[1]) : setCell(args[0], null));
        api.setProperty("getCell", (cn.net.zhijian.quickjs.JSCallFunction) args -> getCell(args[0]));
        api.setProperty("getCells", (cn.net.zhijian.quickjs.JSCallFunction) args -> getCells());
        api.setProperty("meta", (cn.net.zhijian.quickjs.JSCallFunction) args -> meta(args[0]));
        api.setProperty("sheetMeta", (cn.net.zhijian.quickjs.JSCallFunction) args -> sheetMeta(args[0], args[1]));
        api.setProperty("load", (cn.net.zhijian.quickjs.JSCallFunction) args -> load(args[0]));
        api.setProperty("output", (cn.net.zhijian.quickjs.JSCallFunction) args ->
                args.length > 0 ? output(args[0]) : output(null));
        api.setProperty("log", (cn.net.zhijian.quickjs.JSCallFunction) args ->
                args.length > 0 ? log(args[0]) : log(""));
        ctx.setProperty(ctx.getGlobalObject(), "jobx", api);
    }

    // ======================== 方法实现 ========================

    private Object sheet(Object nameArg) {
        String name = String.valueOf(nameArg);
        GeneratorSheet sh = job.sheets.get(name);
        if (sh == null) {
            sh = new GeneratorSheet(name);
            job.sheets.put(name, sh);
        }
        currentSheet = sh;
        return "sheet: " + name + " (cells=" + sh.cells.size() + ")";
    }

    private Object sheets() {
        JSArray arr = ctx.createJSArray();
        int i = 0;
        for (String name : job.sheets.keySet()) {
            arr.set(name, i++);
        }
        return arr;
    }

    private Object setCell(Object locArg, Object propsArg) {
        String loc = String.valueOf(locArg);
        GeneratorSheet sh = ensureCurrentSheet();
        GeneratorCell cell = sh.cells.get(loc);
        if (cell == null) {
            cell = new GeneratorCell();
            cell.location = loc;
            sh.cells.put(loc, cell);
        }
        if (propsArg == null) {
            // 仅创建空 cell
        } else if (propsArg instanceof String) {
            cell.expression = (String) propsArg;
        } else if (propsArg instanceof JSObject) {
            JSObject obj = (JSObject) propsArg;
            String expr = getStringProp(obj, "expression");
            if (expr != null) cell.expression = expr;
            String name = getStringProp(obj, "name");
            if (name != null) cell.name = name;
            String value = getStringProp(obj, "value");
            if (value != null) cell.value = value;
            String comment = getStringProp(obj, "comment");
            if (comment != null) cell.comment = comment;
            String style = getStringProp(obj, "cellStyle");
            if (style != null) cell.cellStyle = style;
            String gstyle = getStringProp(obj, "graphicsStyle");
            if (gstyle != null) cell.graphicsStyle = gstyle;
            String cond = getStringProp(obj, "condition");
            if (cond != null) cell.condition = cond;
            Integer input = getIntProp(obj, "input");
            if (input != null) cell.input = input;
            Integer output = getIntProp(obj, "output");
            if (output != null) cell.output = output;
            Integer ipp = getIntProp(obj, "ipProtected");
            if (ipp != null) cell.ipProtected = ipp;
        } else {
            // 其他类型按字符串表达式处理 / fallback: treat as expression string
            cell.expression = String.valueOf(propsArg);
        }
        return "set " + loc + " @" + sh.name;
    }

    private Object getCell(Object locArg) {
        String loc = String.valueOf(locArg);
        if (currentSheet == null) return null;
        GeneratorCell c = currentSheet.cells.get(loc);
        if (c == null) return null;
        return cellToJSObject(c);
    }

    private Object getCells() {
        JSArray arr = ctx.createJSArray();
        if (currentSheet == null) return arr;
        int i = 0;
        for (GeneratorCell c : currentSheet.cells.values()) {
            arr.set(cellToJSObject(c), i++);
        }
        return arr;
    }

    private Object meta(Object objArg) {
        if (!(objArg instanceof JSObject)) {
            throw new RuntimeException("meta 参数必须是对象");
        }
        JSObject obj = (JSObject) objArg;
        String jv = getStringProp(obj, "JobVersion");
        if (jv != null) job.jobVersion = jv;
        String jt = getStringProp(obj, "JobType");
        if (jt != null) job.jobType = jt;
        String ct = getStringProp(obj, "CameraType");
        if (ct != null) job.cameraType = ct;
        String fv = getStringProp(obj, "FirmwareVersion");
        if (fv != null) job.firmwareVersion = fv;
        return "meta: JobVersion=" + job.jobVersion + ", JobType=" + job.jobType;
    }

    private Object sheetMeta(Object nameArg, Object objArg) {
        String name = String.valueOf(nameArg);
        GeneratorSheet sh = job.sheets.get(name);
        if (sh == null) {
            throw new RuntimeException("sheet 不存在: " + name);
        }
        if (!(objArg instanceof JSObject)) {
            throw new RuntimeException("sheetMeta 第二参数必须是对象");
        }
        JSObject obj = (JSObject) objArg;
        Integer t = getIntProp(obj, "timeout");
        if (t != null) sh.timeout = t;
        Double ct = getDoubleProp(obj, "coreThreshold");
        if (ct != null) sh.coreThreshold = ct;
        Integer pc = getIntProp(obj, "processingCores");
        if (pc != null) sh.processingCores = pc;
        String op = getStringProp(obj, "outputs");
        if (op != null) sh.outputs = op;
        Object cw = obj.getProperty("columnWidths");
        if (cw instanceof JSArray) {
            sh.columnWidths.clear();
            JSArray arr = (JSArray) cw;
            for (int i = 0; i < ctx.length(arr); i++) {
                Object v = ctx.get(arr, i);
                if (v != null) sh.columnWidths.add(Integer.parseInt(String.valueOf(v)));
            }
        }
        Object rh = obj.getProperty("rowHeights");
        if (rh instanceof JSArray) {
            sh.rowHeights.clear();
            JSArray arr = (JSArray) rh;
            for (int i = 0; i < ctx.length(arr); i++) {
                Object v = ctx.get(arr, i);
                if (v != null) sh.rowHeights.add(Integer.parseInt(String.valueOf(v)));
            }
        }
        return "sheetMeta: " + name;
    }

    private Object load(Object fileArg) {
        String path = String.valueOf(fileArg);
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            throw new RuntimeException("文件不存在: " + path);
        }
        try {
            com.cognex.parser.JobxParser.ParsedJob parsed = com.cognex.parser.JobxParser.parse(f);
            // 清空当前模型，用加载的内容替换 / Reset model and replace with loaded
            job.sheets.clear();
            String jv = parsed.meta.get("JobVersion");
            if (jv != null) job.jobVersion = jv;
            String jt = parsed.meta.get("JobType");
            if (jt != null) job.jobType = jt;
            job.cameraType = parsed.meta.getOrDefault("CameraType", "");
            job.firmwareVersion = parsed.meta.getOrDefault("FirmwareVersion", "");
            for (com.cognex.parser.JobxParser.ParsedSheet psh : parsed.sheets) {
                GeneratorSheet gsh = new GeneratorSheet(psh.name);
                for (com.cognex.parser.JobxParser.ParsedCell pc : psh.cells) {
                    GeneratorCell gc = new GeneratorCell();
                    gc.location = pc.location;
                    gc.expression = pc.expression;
                    gc.value = pc.value;
                    gc.name = pc.name;
                    gc.comment = pc.comment;
                    gc.cellStyle = pc.cellStyle;
                    // saved 留 null（用户要求：PatMax/Caliper saved 字段恒为 null）
                    gsh.cells.put(pc.location, gc);
                }
                job.sheets.put(psh.name, gsh);
            }
            if (!job.sheets.isEmpty()) {
                currentSheet = job.sheets.values().iterator().next();
            } else {
                currentSheet = null;
            }
            int cellCount = 0;
            for (GeneratorSheet s : job.sheets.values()) cellCount += s.cells.size();
            return "loaded: " + job.sheets.size() + " sheets, " + cellCount + " cells from " + f.getName();
        } catch (Exception e) {
            throw new RuntimeException("load 失败: " + e.getMessage());
        }
    }

    private Object output(Object optsArg) {
        if (optsArg instanceof JSObject) {
            JSObject obj = (JSObject) optsArg;
            String f = getStringProp(obj, "format");
            if (f != null && !f.isEmpty()) outputConfig.format = f;
            String d = getStringProp(obj, "outDir");
            if (d != null && !d.isEmpty()) outputConfig.outDir = d;
            String n = getStringProp(obj, "baseName");
            if (n != null && !n.isEmpty()) outputConfig.baseName = n;
            Boolean ns = getBoolProp(obj, "noSig");
            if (ns != null) outputConfig.noSig = ns;
        }
        outputCalled = true;
        return "output: format=" + outputConfig.format
                + ", dir=" + (outputConfig.outDir == null ? "(default)" : outputConfig.outDir)
                + ", baseName=" + (outputConfig.baseName == null ? "(default)" : outputConfig.baseName)
                + ", noSig=" + outputConfig.noSig;
    }

    private Object log(Object msgArg) {
        System.out.println("[jobx.log] " + String.valueOf(msgArg));
        return null;
    }

    // ======================== 工具 / utilities ========================

    private GeneratorSheet ensureCurrentSheet() {
        if (currentSheet != null) return currentSheet;
        if (job.sheets.isEmpty()) {
            GeneratorSheet sh = new GeneratorSheet("Sheet1");
            job.sheets.put("Sheet1", sh);
            currentSheet = sh;
            return sh;
        }
        currentSheet = job.sheets.values().iterator().next();
        return currentSheet;
    }

    private JSObject cellToJSObject(GeneratorCell c) {
        JSObject o = ctx.createJSObject();
        o.setProperty("location", c.location);
        o.setProperty("expression", c.expression);
        if (c.value != null) o.setProperty("value", c.value);
        o.setProperty("name", c.name);
        o.setProperty("comment", c.comment);
        o.setProperty("cellStyle", c.cellStyle);
        return o;
    }

    private String getStringProp(JSObject obj, String key) {
        Object v = obj.getProperty(key);
        return v == null ? null : String.valueOf(v);
    }

    private Integer getIntProp(JSObject obj, String key) {
        Object v = obj.getProperty(key);
        if (v == null) return null;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDoubleProp(JSObject obj, String key) {
        Object v = obj.getProperty(key);
        if (v == null) return null;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBoolProp(JSObject obj, String key) {
        Object v = obj.getProperty(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
