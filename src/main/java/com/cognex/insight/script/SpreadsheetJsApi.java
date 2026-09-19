package com.cognex.insight.script;

import cn.net.zhijian.quickjs.JSObject;
import cn.net.zhijian.quickjs.QuickJSContext;
import com.cognex.insight.cogsocket.InSightConnection;
import com.cognex.insight.model.CellResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JavaScript API for spreadsheet operations via InSightConnection.
 * 在 JS 侧提供全局 spreadsheet 对象:
 * setCellValue(location, value) / getCellValue(location, default) /
 * setCellExpression(location, expr) / getCellExpression(location, default) /
 * trigger() / setOnline(online) / setLiveMode(live)
 */
public class SpreadsheetJsApi {

    private final QuickJSContext ctx;
    private final InSightConnection connection;
    private JSObject spreadsheet;

    public SpreadsheetJsApi(QuickJSContext ctx, InSightConnection connection) {
        this.ctx = ctx;
        this.connection = connection;
    }

    public void register() {
        spreadsheet = ctx.createJSObject();
        spreadsheet.setProperty("setCellValue", (cn.net.zhijian.quickjs.JSCallFunction) args -> setCellValue(args[0], args[1]));
        spreadsheet.setProperty("getCellValue", (cn.net.zhijian.quickjs.JSCallFunction) args ->
                args.length > 1 ? getCellValue(args[0], args[1]) : getCellValue(args[0], null));
        spreadsheet.setProperty("setCellExpression", (cn.net.zhijian.quickjs.JSCallFunction) args -> setCellExpression(args[0], args[1]));
        spreadsheet.setProperty("getCellExpression", (cn.net.zhijian.quickjs.JSCallFunction) args ->
                args.length > 1 ? getCellExpression(args[0], args[1]) : getCellExpression(args[0], null));
        spreadsheet.setProperty("trigger", (cn.net.zhijian.quickjs.JSCallFunction) args -> trigger());
        spreadsheet.setProperty("setOnline", (cn.net.zhijian.quickjs.JSCallFunction) args -> setOnline(args[0]));
        spreadsheet.setProperty("setLiveMode", (cn.net.zhijian.quickjs.JSCallFunction) args -> setLiveMode(args[0]));
        ctx.setProperty(ctx.getGlobalObject(), "spreadsheet", spreadsheet);
    }

    private Object jsToJava(Object value) {
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (int) d;
            }
            return d;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            String s = (String) value;
            try {
                if (s.contains(".")) {
                    return Double.parseDouble(s);
                }
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return s;
            }
        }
        return value;
    }

    private void checkConnected() {
        if (connection == null || !connection.isConnected()) {
            throw new RuntimeException("未连接到相机");
        }
    }

    private Object setCellValue(Object cellName, Object value) {
        checkConnected();
        String cell = String.valueOf(cellName);
        Object javaValue = jsToJava(value);
        try {
            connection.setCellValue(cell, javaValue).get(10, TimeUnit.SECONDS);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("设置单元格值失败: " + e.getMessage());
        }
    }

    private Object getCellValue(Object cellName, Object defaultValue) {
        checkConnected();
        String cell = String.valueOf(cellName);
        try {
            List<CellResult> results = connection.queryCellResults(new String[]{cell})
                    .get(10, TimeUnit.SECONDS);
            if (results != null && !results.isEmpty()) {
                CellResult cr = results.get(0);
                if (!cr.error) {
                    // 优先使用 data 字段，如果没有则尝试从 name 字段获取
                    String data = "";
                    if (cr.data != null) {
                        data = cr.data.toString();
                    } else if (cr.name != null && !cr.name.isEmpty()) {
                        data = cr.name;
                    }
                    if (!data.isEmpty()) {
                        try {
                            if (data.contains(".")) {
                                return Double.parseDouble(data);
                            }
                            return Integer.parseInt(data);
                        } catch (NumberFormatException e) {
                            return data;
                        }
                    }
                }
            }
            return defaultValue;
        } catch (Exception e) {
            throw new RuntimeException("获取单元格值失败: " + e.getMessage());
        }
    }

    private Object setCellExpression(Object cellName, Object expression) {
        checkConnected();
        String cell = String.valueOf(cellName);
        String expr = String.valueOf(expression);
        try {
            connection.setCellExpression(cell, expr).get(10, TimeUnit.SECONDS);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("设置单元格表达式失败: " + e.getMessage());
        }
    }

    private Object getCellExpression(Object cellName, Object defaultValue) {
        checkConnected();
        String cell = String.valueOf(cellName);
        try {
            // 先尝试 getCellExpression API
            String expr = connection.getCellExpression(cell).get(10, TimeUnit.SECONDS);

            // 如果直接获取为空，fallback 到 queryCellResults
            if (expr == null || expr.isEmpty()) {
                List<CellResult> results = connection.queryCellResults(new String[]{cell})
                        .get(10, TimeUnit.SECONDS);
                if (results != null && !results.isEmpty()) {
                    CellResult cr = results.get(0);
                    if (cr.expression != null && !cr.expression.isEmpty()) {
                        expr = cr.expression;
                    }
                }
            }

            if (expr != null && !expr.isEmpty()) {
                return expr;
            }
            return defaultValue;
        } catch (Exception e) {
            throw new RuntimeException("获取单元格表达式失败: " + e.getMessage());
        }
    }

    private Object trigger() {
        checkConnected();
        try {
            connection.manualTrigger().get(10, TimeUnit.SECONDS);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("手动触发失败: " + e.getMessage());
        }
    }

    private Object setOnline(Object online) {
        checkConnected();
        try {
            connection.setSoftOnline(Boolean.parseBoolean(String.valueOf(online)))
                    .get(10, TimeUnit.SECONDS);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("设置在线状态失败: " + e.getMessage());
        }
    }

    private Object setLiveMode(Object liveMode) {
        checkConnected();
        try {
            connection.setLiveMode(Boolean.parseBoolean(String.valueOf(liveMode)))
                    .get(10, TimeUnit.SECONDS);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("设置实时模式失败: " + e.getMessage());
        }
    }
}
