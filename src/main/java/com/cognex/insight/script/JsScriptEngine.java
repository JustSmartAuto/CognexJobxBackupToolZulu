package com.cognex.insight.script;

import cn.net.zhijian.quickjs.JSObject;
import cn.net.zhijian.quickjs.QuickJSContext;
import com.cognex.insight.cogsocket.InSightConnection;

/**
 * JavaScript (QuickJS) 脚本执行引擎，内置 spreadsheet API 绑定。
 * console.log/info/warn/error 输出被捕获到结果中。
 */
public class JsScriptEngine {

    private final InSightConnection connection;
    private QuickJSContext ctx;
    private SpreadsheetJsApi api;
    private StringBuilder outputBuffer;

    public JsScriptEngine(InSightConnection connection) {
        this.connection = connection;
        // QuickJS 上下文延迟初始化：该库（含 Thread.threadId 等调用）需要较新 JDK，
        // 且会加载本地库；不在构造时立即创建，避免影响备份/导出等其他标签页。
    }

    /** 惰性创建 QuickJS 上下文；失败时抛出异常由调用方提示。 */
    private synchronized void ensureContext() {
        if (ctx != null) {
            return;
        }
        ctx = QuickJSContext.create();
        outputBuffer = new StringBuilder();
        ctx.setConsole(new QuickJSContext.Console() {
            @Override
            public void debug(String msg) {
                outputBuffer.append("[debug] ").append(msg).append('\n');
            }

            @Override
            public void info(String msg) {
                outputBuffer.append(msg).append('\n');
            }

            @Override
            public void warn(String msg) {
                outputBuffer.append("[warn] ").append(msg).append('\n');
            }

            @Override
            public void error(String msg) {
                outputBuffer.append("[error] ").append(msg).append('\n');
            }
        });
        api = new SpreadsheetJsApi(ctx, connection);
        api.register();
    }

    private void closeContext() {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
            ctx = null;
        }
    }

    /**
     * 执行 JavaScript 脚本并返回捕获的输出。
     *
     * @param script JS 脚本源码
     * @return 执行结果（输出与返回值或错误）
     */
    public ScriptResult execute(String script) {
        return execute(script, false);
    }

    /**
     * 执行 JavaScript 脚本并返回捕获的输出。
     *
     * @param script JS 脚本源码
     * @param interactive 是否为交互式执行（保留参数以兼容旧调用，JS 顶层表达式返回值天然可见，无需包装）
     * @return 执行结果（输出与返回值或错误）
     */
    public ScriptResult execute(String script, boolean interactive) {
        String output = "";
        try {
            ensureContext();
            Object result = ctx.evaluate(script, "script.js");
            output = outputBuffer.toString();
            return new ScriptResult(true, output, formatResult(result));
        } catch (Throwable t) {
            // 捕获 Throwable：QuickJS 初始化失败可能是 NoSuchMethodError（JDK 版本过低，
            // 该库需要 JDK 19+）或 UnsatisfiedLinkError（本地库加载失败）
            if (outputBuffer != null) {
                output = outputBuffer.toString();
            }
            return new ScriptResult(false, output, friendlyError(t));
        } finally {
            // 清空本次输出，避免重复追加到下一次执行
            if (outputBuffer != null) {
                outputBuffer.setLength(0);
            }
        }
    }

    /** 将 QuickJS 引擎/本地库相关的底层错误转换为可读提示。 */
    private String friendlyError(Throwable t) {
        String msg = t.getMessage() != null ? t.getMessage() : t.toString();
        if (t instanceof NoSuchMethodError || t instanceof UnsupportedClassVersionError) {
            return "QuickJS 脚本引擎需要 JDK 19 或更高版本，当前 Java 版本过低。"
                    + "备份与导出功能不受影响。原始错误: " + msg;
        }
        if (t instanceof UnsatisfiedLinkError) {
            return "QuickJS 本地库加载失败: " + msg;
        }
        return msg;
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "undefined";
        }
        if (result instanceof JSObject) {
            try {
                return ctx.stringify((JSObject) result);
            } catch (Exception e) {
                return String.valueOf(result);
            }
        }
        return String.valueOf(result);
    }

    /**
     * 重置 JS 环境（丢弃全部变量与函数，下次执行时重建上下文）。
     * 失败（如 JDK 版本过低）时抛出 RuntimeException，由 UI 层捕获提示。
     */
    public void reset() {
        closeContext();
        try {
            ensureContext();
        } catch (Throwable t) {
            throw new RuntimeException(friendlyError(t));
        }
    }

    /**
     * 释放 QuickJS 上下文资源。
     */
    public void close() {
        closeContext();
    }

    public static class ScriptResult {
        public final boolean success;
        public final String output;
        public final String result;

        public ScriptResult(boolean success, String output, String result) {
            this.success = success;
            this.output = output;
            this.result = result;
        }
    }
}
