package com.cognex.backup;

import com.cognex.backup.ui.MainFrame;
import com.cognex.parser.JobxParser;
import com.cognex.parser.ParserXlsxExporter;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.io.File;
import java.util.Date;

public class Main {
    public static void main(String[] args) {
        // CLI 子命令模式：首个参数为子命令时进入 CLI；否则启动 GUI / CLI subcommand mode
        if (args.length > 0) {
            String cmd = args[0];
            if ("parse".equals(cmd) || "p".equals(cmd)) {
                int code = runParse(args);
                System.exit(code);
            } else if ("--help".equals(cmd) || "-h".equals(cmd) || "help".equals(cmd)) {
                printHelp();
                System.exit(0);
            } else if ("--version".equals(cmd) || "-v".equals(cmd)) {
                System.out.println("Cognex Jobx 工具箱 1.0.0");
                System.exit(0);
            }
            // 未知首参数：当作 GUI 启动（向后兼容）/ Unknown first arg: fall through to GUI (backward compat)
        }

        // GUI 模式（默认）/ GUI mode (default)
        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception e) {
                System.err.println("Failed to initialize FlatLaf: " + e.getMessage());
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /**
     * CLI 子命令：离线解析 .jobx / .cxdx → xlsx。
     * 用法：java -jar xxx.jar parse <file.jobx|file.cxdx> [--out <dir>]
     * CLI subcommand: offline parse .jobx / .cxdx -> xlsx.
     * Usage: java -jar xxx.jar parse <file.jobx|file.cxdx> [--out <dir>]
     */
    private static int runParse(String[] args) {
        if (args.length < 2 || args[1].isEmpty()) {
            System.err.println("用法: java -jar <jar> parse <file.jobx|file.cxdx> [--out <dir>]");
            System.err.println("详见 --help");
            return 2;
        }
        File src = new File(args[1]);
        if (!src.exists() || !src.isFile()) {
            System.err.println("文件不存在: " + src.getAbsolutePath());
            return 2;
        }
        File outDir = null;
        for (int i = 2; i < args.length; i++) {
            if ("--out".equals(args[i]) || "-o".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("--out 缺少参数");
                    return 2;
                }
                outDir = new File(args[++i]);
            } else {
                System.err.println("未知参数: " + args[i]);
                return 2;
            }
        }
        long t0 = System.currentTimeMillis();
        try {
            System.out.println("解析: " + src.getAbsolutePath());
            JobxParser.ParsedJob job = JobxParser.parse(src);
            int totalCells = 0;
            for (JobxParser.ParsedSheet sh : job.sheets) totalCells += sh.cells.size();
            System.out.println("Sheets: " + job.sheets.size() + ", cells: " + totalCells);
            File out = ParserXlsxExporter.export(job, new Date(), outDir);
            long ms = System.currentTimeMillis() - t0;
            System.out.println("导出: " + out.getAbsolutePath() + " (" + out.length() + " bytes, " + ms + " ms)");
            return 0;
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private static void printHelp() {
        System.out.println("Cognex Jobx 工具箱 — 命令行用法");
        System.out.println();
        System.out.println("启动 GUI（默认，无参数）：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar");
        System.out.println();
        System.out.println("离线解析 .jobx / .cxdx → xlsx：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar parse <file> [--out <dir>]");
        System.out.println("    <file>      .jobx 或 .cxdx 文件路径");
        System.out.println("    --out <dir> 输出目录（可选，默认源文件同目录）");
        System.out.println("    输出: {stem}_yyyyMMdd_HHmmss.xlsx，含「单元格」+「位置布局-{sheetName}」两个 sheet");
        System.out.println();
        System.out.println("其他：");
        System.out.println("  java -jar ... --help | -h       显示本帮助");
        System.out.println("  java -jar ... --version | -v    显示版本号");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar parse \"jobx/天窗程序模板.jobx\"");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar parse \"jobx/天窗程序模板.jobx\" --out D:/exports");
    }
}

