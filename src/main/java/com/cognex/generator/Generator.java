package com.cognex.generator;

import com.cognex.parser.JobxParser.ParsedCell;
import com.cognex.parser.JobxParser.ParsedJob;
import com.cognex.parser.JobxParser.ParsedSheet;
import com.cognex.parser.ParserXlsxExporter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 生成器输出助手：根据 OutputConfig 把 GeneratorJob 写成 .jobx / .cxdx / .xlsx。
 *
 * 输出文件名规则与 ParserXlsxExporter 一致：{baseName}_yyyyMMdd_HHmmss.<ext>
 * 默认输出目录：GUI = jar 目录下 generated/；CLI = 脚本父目录。
 */
public final class Generator {

    private Generator() {
    }

    /** 把 GeneratorJob 转换为 ParsedJob，复用 ParserXlsxExporter 导出 xlsx。 */
    public static ParsedJob toParsedJob(GeneratorJob job, String baseName) {
        // sourceFile 仅用于 ParserXlsxExporter 取 stem 作为输出文件名前缀
        ParsedJob pj = new ParsedJob(new File(baseName + ".jobx"));
        pj.meta.put("JobVersion", job.jobVersion);
        pj.meta.put("JobType", job.jobType);
        if (job.cameraType != null && !job.cameraType.isEmpty()) {
            pj.meta.put("CameraType", job.cameraType);
        }
        if (job.firmwareVersion != null && !job.firmwareVersion.isEmpty()) {
            pj.meta.put("FirmwareVersion", job.firmwareVersion);
        }
        for (GeneratorSheet gs : job.sheets.values()) {
            ParsedSheet ps = new ParsedSheet(gs.name);
            for (GeneratorCell gc : gs.cells.values()) {
                ParsedCell pc = new ParsedCell();
                pc.location = gc.location;
                pc.expression = gc.expression;
                pc.value = gc.value != null ? gc.value : "";
                pc.name = gc.name;
                pc.comment = gc.comment;
                pc.cellStyle = gc.cellStyle;
                // savedBytes 留 null（生成器不写训练态）
                ps.cells.add(pc);
            }
            pj.sheets.add(ps);
        }
        return pj;
    }

    /**
     * 按 OutputConfig 写出 .jobx / .cxdx / .xlsx。
     * format: "jobx" | "cxdx" | "xlsx" | "all"
     *
     * @param job            生成器作业模型
     * @param cfg            输出参数（baseName/outDir 为 null 时使用默认）
     * @param defaultOutDir  cfg.outDir 为 null 时使用
     * @param defaultBaseName cfg.baseName 为 null 时使用
     * @param log            日志回调（每行通过 log.accept(msg) 输出）
     * @return 失败的格式数（0 = 全部成功）
     */
    public static int writeOutputs(GeneratorJob job, GeneratorJsApi.OutputConfig cfg,
                                   File defaultOutDir, String defaultBaseName,
                                   Consumer<String> log) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File outDir = cfg.outDir != null ? new File(cfg.outDir) : defaultOutDir;
        String baseName = (cfg.baseName != null && !cfg.baseName.isEmpty()) ? cfg.baseName : defaultBaseName;

        if (outDir != null && !outDir.exists() && !outDir.mkdirs()) {
            log.accept("无法创建输出目录: " + outDir.getAbsolutePath());
            return formatsIn(cfg).length;  // 全失败
        }

        int failed = 0;
        for (String fmt : formatsIn(cfg)) {
            try {
                File outFile = new File(outDir, baseName + "_" + ts + "." + fmt);
                switch (fmt) {
                    case "jobx":
                        JobxWriter.writeJobx(job, outFile, !cfg.noSig);
                        log.accept("✓ " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");
                        break;
                    case "cxdx":
                        GeneratorSheet sh = pickCxdxSheet(job);
                        JobxWriter.writeCxdx(sh, outFile, !cfg.noSig);
                        log.accept("✓ " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");
                        break;
                    case "xlsx":
                        ParsedJob pj = toParsedJob(job, baseName);
                        File xlsxOut = ParserXlsxExporter.export(pj, new Date(), outDir);
                        log.accept("✓ " + xlsxOut.getAbsolutePath() + " (" + xlsxOut.length() + " bytes)");
                        break;
                    default:
                        log.accept("未知格式: " + fmt);
                        failed++;
                }
            } catch (Exception e) {
                log.accept("✗ " + fmt + ": " + e.getMessage());
                failed++;
            }
        }
        return failed;
    }

    /** 返回格式数组：解析 "all" 为 [jobx, cxdx, xlsx]，否则单元素。 */
    private static String[] formatsIn(GeneratorJsApi.OutputConfig cfg) {
        String f = cfg.format == null ? "all" : cfg.format.toLowerCase();
        if ("all".equals(f)) return new String[]{"jobx", "cxdx", "xlsx"};
        return new String[]{f};
    }

    /** cxdx 只能写一个 sheet：取 currentSheet 或第一个 sheet；都没有则报错。 */
    private static GeneratorSheet pickCxdxSheet(GeneratorJob job) {
        for (GeneratorSheet sh : job.sheets.values()) return sh;
        throw new IllegalStateException("没有 sheet 可写入 cxdx");
    }

    /** 工具：列出当前所有 sheet（GUI 提示用）。 */
    public static String summarize(GeneratorJob job) {
        int total = 0;
        for (GeneratorSheet sh : job.sheets.values()) total += sh.cells.size();
        return job.sheets.size() + " sheet(s), " + total + " cell(s)";
    }

    /** 工具：列出 sheet 与单元格明细（CLI 输出用）。 */
    public static String describe(GeneratorJob job) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GeneratorSheet> e : job.sheets.entrySet()) {
            GeneratorSheet sh = e.getValue();
            sb.append("Sheet \"").append(sh.name).append("\": ").append(sh.cells.size()).append(" cell(s)\n");
            for (GeneratorCell c : sh.cells.values()) {
                sb.append("  ").append(c.location).append(": ").append(c.expression);
                if (c.name != null && !c.name.isEmpty()) sb.append(" [").append(c.name).append("]");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 工具：列出所有 sheet 名（CLI 摘要用）。 */
    public static String listSheets(List<GeneratorSheet> sheets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sheets.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sheets.get(i).name);
        }
        return sb.toString();
    }
}
