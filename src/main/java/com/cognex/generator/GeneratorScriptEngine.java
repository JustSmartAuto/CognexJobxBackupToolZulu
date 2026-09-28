package com.cognex.generator;

import cn.net.zhijian.quickjs.JSObject;
import cn.net.zhijian.quickjs.QuickJSContext;

import java.io.File;
import java.util.function.Consumer;

/**
 * 生成器脚本引擎：QuickJS + jobx API，执行 JS 脚本后自动写出 .jobx/.cxdx/.xlsx。
 *
 * 设计要点（参考 JsScriptEngine）：
 * - QuickJS 上下文延迟初始化（首次执行脚本时才加载本地库）
 * - JDK < 19 下脚本功能给出友好错误（库内部用 Thread.threadId()），不影响其他功能
 * - 脚本结束若未显式调用 jobx.output()，自动按 format="all" 输出到默认目录
 */
public class GeneratorScriptEngine {

    private final File defaultOutDir;
    private final String defaultBaseName;
    private final Consumer<String> log;
    /** 脚本未显式调用 jobx.output() 时使用的默认配置 / Default config when script didn't call output(). */
    private final GeneratorJsApi.OutputConfig defaultCfg = new GeneratorJsApi.OutputConfig();
    private QuickJSContext ctx;
    private GeneratorJsApi api;
    private StringBuilder outputBuffer;

    public GeneratorScriptEngine(File defaultOutDir, String defaultBaseName, Consumer<String> log) {
        this.defaultOutDir = defaultOutDir;
        this.defaultBaseName = defaultBaseName;
        this.log = log;
    }

    /** CLI 覆盖默认 format / noSig（脚本未调用 jobx.output() 时生效）。 */
    public void setDefaultFormat(String format, boolean noSig) {
        defaultCfg.format = format == null ? "all" : format;
        defaultCfg.noSig = noSig;
    }

    /** 惰性创建 QuickJS 上下文；失败时抛出异常由调用方提示。 */
    private synchronized void ensureContext() {
        if (ctx != null) return;
        ctx = QuickJSContext.create();
        outputBuffer = new StringBuilder();
        ctx.setConsole(new QuickJSContext.Console() {
            @Override public void debug(String msg) { outputBuffer.append("[debug] ").append(msg).append('\n'); }
            @Override public void info(String msg) { outputBuffer.append(msg).append('\n'); }
            @Override public void warn(String msg) { outputBuffer.append("[warn] ").append(msg).append('\n'); }
            @Override public void error(String msg) { outputBuffer.append("[error] ").append(msg).append('\n'); }
        });
        api = new GeneratorJsApi(ctx, new GeneratorJob());
        api.register();
    }

    private void closeContext() {
        if (ctx != null) {
            try { ctx.close(); } catch (Exception ignored) { }
            ctx = null;
            api = null;
        }
    }

    /**
     * 执行 JS 脚本，结束后自动写出文件（除非脚本中已调用 jobx.output() 并设置 baseName/outDir）。
     * @return ScriptResult（success / output / message）
     */
    public ScriptResult execute(String script) {
        String output;
        try {
            ensureContext();
            Object result = ctx.evaluate(script, "generator.js");
            output = outputBuffer.toString();

            // 脚本未显式调用 jobx.output() 时，使用默认 OutputConfig（CLI/GUI 设置）
            GeneratorJsApi.OutputConfig cfg;
            if (api.wasOutputCalled()) {
                cfg = api.getOutputConfig();
            } else {
                cfg = defaultCfg;
            }

            // 写出文件 / write outputs
            int failed = Generator.writeOutputs(api.getJob(), cfg,
                    defaultOutDir, defaultBaseName, log::accept);

            String summary = "sheets/cells: " + Generator.summarize(api.getJob())
                    + (failed > 0 ? "（" + failed + " 个格式失败）" : "");
            return new ScriptResult(true, output, summary + " | result=" + formatResult(result));
        } catch (Throwable t) {
            output = outputBuffer != null ? outputBuffer.toString() : "";
            return new ScriptResult(false, output, friendlyError(t));
        } finally {
            if (outputBuffer != null) outputBuffer.setLength(0);
        }
    }

    /** 重置 JS 环境：销毁上下文，下次执行时重建。 */
    public synchronized void reset() {
        closeContext();
    }

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
        if (result == null) return "undefined";
        if (result instanceof JSObject) {
            try { return ctx.stringify((JSObject) result); }
            catch (Exception e) { return String.valueOf(result); }
        }
        return String.valueOf(result);
    }

    /** 脚本执行结果 / Script result. */
    public static class ScriptResult {
        public final boolean success;
        public final String output;
        public final String message;

        public ScriptResult(boolean success, String output, String message) {
            this.success = success;
            this.output = output;
            this.message = message;
        }
    }
}
