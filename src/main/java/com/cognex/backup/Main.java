package com.cognex.backup;

import com.cognex.backup.config.ConfigManager;
import com.cognex.backup.ftp.FtpClient;
import com.cognex.backup.model.CameraConfig;
import com.cognex.backup.ui.MainFrame;
import com.cognex.export.ExportTask;
import com.cognex.export.config.ExportConfigManager;
import com.cognex.export.model.ExportCamera;
import com.cognex.parser.JobxParser;
import com.cognex.parser.ParserXlsxExporter;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // CLI 子命令模式：首个参数为子命令时进入 CLI；否则启动 GUI / CLI subcommand mode
        if (args.length > 0) {
            String cmd = args[0];
            if ("parse".equals(cmd) || "p".equals(cmd)) {
                int code = runParse(args);
                System.exit(code);
            } else if ("backup".equals(cmd) || "b".equals(cmd)) {
                int code = runBackup(args);
                System.exit(code);
            } else if ("export".equals(cmd) || "e".equals(cmd)) {
                int code = runExport(args);
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

    /**
     * CLI 子命令：从 backup-config.json 读相机列表，FTP/FTPS 递归下载 .jobx。
     * CLI subcommand: read cameras from backup-config.json and recursively download .jobx via FTP/FTPS.
     *
     * 用法: java -jar xxx.jar backup [--camera <name> | --all] [--config <file>] [--out <dir>]
     * Usage: java -jar xxx.jar backup [--camera <name> | --all] [--config <file>] [--out <dir>]
     */
    private static int runBackup(String[] args) {
        String cameraName = null;
        boolean all = false;
        File configFile = null;
        File outDir = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--camera".equals(a) || "-c".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--camera 缺少参数"); return 2; }
                cameraName = args[++i];
            } else if ("--all".equals(a)) {
                all = true;
            } else if ("--config".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--config 缺少参数"); return 2; }
                configFile = new File(args[++i]);
            } else if ("--out".equals(a) || "-o".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--out 缺少参数"); return 2; }
                outDir = new File(args[++i]);
            } else if ("--help".equals(a) || "-h".equals(a)) {
                System.out.println("用法: java -jar <jar> backup [--camera <name> | --all] [--config <file>] [--out <dir>]");
                System.out.println("  --camera <name>  仅备份指定相机");
                System.out.println("  --all            备份配置中的全部相机（默认）");
                System.out.println("  --config <file>  使用指定 backup-config.json（默认 jar 同目录）");
                System.out.println("  --out <dir>      覆盖备份根目录（默认按相机配置或 jar 目录下 backups/）");
                return 0;
            } else {
                System.err.println("未知参数: " + a);
                return 2;
            }
        }

        if (cameraName != null && all) {
            System.err.println("--camera 与 --all 互斥");
            return 2;
        }

        ConfigManager config = configFile != null ? new ConfigManager(configFile) : new ConfigManager();
        List<CameraConfig> cameras = config.getCameras();
        if (cameras.isEmpty()) {
            System.err.println("配置中没有相机");
            return 1;
        }

        List<CameraConfig> targets = new ArrayList<>();
        if (cameraName != null) {
            for (CameraConfig c : cameras) {
                if (cameraName.equals(c.getName())) { targets.add(c); break; }
            }
            if (targets.isEmpty()) {
                System.err.println("找不到相机: " + cameraName);
                StringBuilder sb = new StringBuilder("可用相机: ");
                for (int i = 0; i < cameras.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(cameras.get(i).getName());
                }
                System.err.println(sb.toString());
                return 2;
            }
        } else {
            targets.addAll(cameras);
        }

        FtpClient ftp = new FtpClient();
        int success = 0, failed = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < targets.size(); i++) {
            CameraConfig c = targets.get(i);
            if (outDir != null) c.setBackupDirectory(outDir.getAbsolutePath());  // CLI 覆盖备份根目录
            System.out.println("[" + (i + 1) + "/" + targets.size() + "] 备份: " + c.getName() + " (" + c.getIp() + ")"
                    + (c.isFtpsEnabled() ? " [FTPS]" : " [FTP]"));
            FtpClient.BackupResult r = ftp.backupJobx(c);
            if (r.success) {
                success++;
                System.out.println("  ✓ " + r.message + " -> " + r.backupPath);
            } else {
                failed++;
                System.out.println("  ✗ " + r.message);
            }
        }
        long ms = System.currentTimeMillis() - t0;
        System.out.println("完成：成功 " + success + " 台，失败 " + failed + " 台，用时 " + ms + " ms");
        return failed == 0 ? 0 : 1;
    }

    /**
     * CLI 子命令：从 export-config.json 读相机列表，FTP 枚举 .jobx + CogSocket HMI 读单元格 → xlsx。
     * CLI subcommand: enumerate .jobx via FTP, read cells via CogSocket HMI, export xlsx per job.
     *
     * 用法: java -jar xxx.jar export [--camera <name> | --all] [--config <file>] [--out <dir>] [--skip-expression]
     * Usage: java -jar xxx.jar export [--camera <name> | --all] [--config <file>] [--out <dir>] [--skip-expression]
     */
    private static int runExport(String[] args) {
        String cameraName = null;
        boolean all = false;
        File configFile = null;
        File outDir = null;
        boolean skipExpr = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--camera".equals(a) || "-c".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--camera 缺少参数"); return 2; }
                cameraName = args[++i];
            } else if ("--all".equals(a)) {
                all = true;
            } else if ("--config".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--config 缺少参数"); return 2; }
                configFile = new File(args[++i]);
            } else if ("--out".equals(a) || "-o".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("--out 缺少参数"); return 2; }
                outDir = new File(args[++i]);
            } else if ("--skip-expression".equals(a) || "--no-expr".equals(a)) {
                skipExpr = true;
            } else if ("--help".equals(a) || "-h".equals(a)) {
                System.out.println("用法: java -jar <jar> export [--camera <name> | --all] [--config <file>] [--out <dir>] [--skip-expression]");
                System.out.println("  --camera <name>       仅导出指定相机");
                System.out.println("  --all                 导出配置中的全部相机（默认）");
                System.out.println("  --config <file>       使用指定 export-config.json（默认 jar 同目录）");
                System.out.println("  --out <dir>           覆盖输出根目录（默认 jar 目录下 exports/<相机名>/<时间戳>/）");
                System.out.println("  --skip-expression     跳过表达式回读，加快速度");
                return 0;
            } else {
                System.err.println("未知参数: " + a);
                return 2;
            }
        }

        if (cameraName != null && all) {
            System.err.println("--camera 与 --all 互斥");
            return 2;
        }

        ExportConfigManager config = configFile != null ? new ExportConfigManager(configFile) : new ExportConfigManager();
        List<ExportCamera> cameras = config.getCameras();
        if (cameras.isEmpty()) {
            System.err.println("配置中没有相机");
            return 1;
        }

        List<ExportCamera> targets = new ArrayList<>();
        if (cameraName != null) {
            for (ExportCamera c : cameras) {
                if (cameraName.equals(c.getName())) { targets.add(c); break; }
            }
            if (targets.isEmpty()) {
                System.err.println("找不到相机: " + cameraName);
                StringBuilder sb = new StringBuilder("可用相机: ");
                for (int i = 0; i < cameras.size(); i++) {
                    if (i > 0) sb.append(", ");
                    ExportCamera c = cameras.get(i);
                    sb.append(c.getName().isEmpty() ? c.getIp() : c.getName());
                }
                System.err.println(sb.toString());
                return 2;
            }
        } else {
            targets.addAll(cameras);
        }

        int success = 0, failed = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < targets.size(); i++) {
            ExportCamera c = targets.get(i);
            String label = c.getName().isEmpty() ? c.getIp() : c.getName();
            System.out.println("[" + (i + 1) + "/" + targets.size() + "] 导出: " + label
                    + " (" + c.getIp() + ":" + c.getWsPort() + ")" + (c.isFtps() ? " [FTPS]" : " [FTP]"));

            // 构造 ExportTask.Params（与 GUI 批量导出一致：--out 给定时按 输出根/相机名/时间戳/ 组织）
            ExportTask.Params p = new ExportTask.Params();
            p.ip = c.getIp();
            p.wsPort = c.getWsPort();
            p.user = c.getUser().isEmpty() ? "admin" : c.getUser();
            p.password = c.getPassword();
            p.ftpPort = c.getFtpPort();
            p.ftps = c.isFtps();
            p.trustAllCerts = c.isTrustAllCerts();
            p.noExpr = skipExpr;
            if (outDir != null) {
                p.outRoot = new File(outDir, label).getAbsolutePath();
            }

            ExportTask task = new ExportTask(p, msg -> System.out.println("  " + msg));
            String error;
            try {
                error = task.run();
            } catch (Exception ex) {
                error = "导出异常: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            if (error == null) {
                success++;
                System.out.println("  ✓ " + label + " 导出成功");
            } else {
                failed++;
                System.out.println("  ✗ " + error);
            }
        }
        long ms = System.currentTimeMillis() - t0;
        System.out.println("完成：成功 " + success + " 台，失败 " + failed + " 台，用时 " + ms + " ms");
        return failed == 0 ? 0 : 1;
    }

    private static void printHelp() {
        System.out.println("Cognex Jobx 工具箱 — 命令行用法");
        System.out.println();
        System.out.println("启动 GUI（默认，无参数）：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar");
        System.out.println();
        System.out.println("离线解析 .jobx / .cxdx → xlsx（不连相机）：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar parse <file> [--out <dir>]");
        System.out.println("    <file>      .jobx 或 .cxdx 文件路径");
        System.out.println("    --out <dir> 输出目录（可选，默认源文件同目录）");
        System.out.println("    输出: {stem}_yyyyMMdd_HHmmss.xlsx，含「单元格」+「位置布局-{sheetName}」两个 sheet");
        System.out.println();
        System.out.println("备份相机作业（FTP/FTPS 递归下载 .jobx + .jobx.sig）：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar backup [--camera <name> | --all] [--config <file>] [--out <dir>]");
        System.out.println("    --camera <name>  仅备份指定相机");
        System.out.println("    --all            备份 backup-config.json 中全部相机（默认）");
        System.out.println("    --config <file>  覆盖 backup-config.json 路径（默认 jar 同目录）");
        System.out.println("    --out <dir>      覆盖备份根目录（默认按相机配置或 jar 目录下 backups/）");
        System.out.println("    输出: <备份根>/<相机名>/<yyyyMMddHHmmss>/");
        System.out.println();
        System.out.println("导出相机作业到 xlsx（FTP 枚举 + CogSocket HMI 读单元格值/表达式）：");
        System.out.println("  java -jar jobx文件备份助手_<时间戳>.jar export [--camera <name> | --all] [--config <file>] [--out <dir>] [--skip-expression]");
        System.out.println("    --camera <name>       仅导出指定相机");
        System.out.println("    --all                 导出 export-config.json 中全部相机（默认）");
        System.out.println("    --config <file>       覆盖 export-config.json 路径（默认 jar 同目录）");
        System.out.println("    --out <dir>           覆盖输出根目录（默认 jar 目录下 exports/<相机名>/<时间戳>/）");
        System.out.println("    --skip-expression     跳过表达式回读，加快速度");
        System.out.println("    输出: 每台相机每个作业一个 xlsx，含「单元格」+「位置布局」两个 sheet");
        System.out.println();
        System.out.println("其他：");
        System.out.println("  java -jar ... --help | -h       显示本帮助");
        System.out.println("  java -jar ... --version | -v    显示版本号");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar parse \"jobx/天窗程序模板.jobx\"");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar parse \"jobx/天窗程序模板.jobx\" --out D:/exports");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar backup --camera Line1");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar backup --all --out D:/backups");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar export --camera Line1 --skip-expression");
        System.out.println("  java -jar jobx文件备份助手_20260928105112.jar export --all --out D:/exports");
    }
}

